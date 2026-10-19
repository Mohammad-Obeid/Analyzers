param(
    [string]$BaseUrl = 'http://localhost:8082',
    [string]$OrgUnitUid,
    [ValidateRange(1, 2147483647)][int]$ProgramVersion = 2,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
if (-not $OrgUnitUid) { throw 'Pass -OrgUnitUid for the organisation unit.' }
if (-not $env:DHIS2_USER -or -not $env:DHIS2_PASSWORD) {
    throw 'Set DHIS2_USER and DHIS2_PASSWORD in the process environment.'
}

# Stable identifiers preserve the existing DHIS2 program and its associations.
$programUid = 'CAB738DA06F'
$stageUid = 'CC615299A29'
$identifiers = @{
    BARCODE = @{ de = 'C6713153A46'; stageDe = 'C2F94A2B29C' }
    ANALYZER_ID = @{ de = 'plTBJagjGMi'; stageDe = 'lyZEfjdzK37' }
    ANALYZER_NAME = @{ de = 'xpNOZE09SWL'; stageDe = 'GndNKuMpcl3' }
    PATIENT_CODE = @{ de = 'I0daj7N4Sih'; stageDe = 'McHh6NczHRQ' }
    PATIENT_NAME = @{ de = 'oCHnvII6iUQ'; stageDe = 'rxgP6P7qxPP' }
    PATIENT_GENDER = @{ de = 'zK6i0HDVTtr'; stageDe = 'zZAJdmBdyCU' }
    PATIENT_AGE = @{ de = 'TMmFsuXnTR8'; stageDe = 'KPCt3UPKHon' }
    WBC = @{ de = 'C83F5365060'; stageDe = 'CDF0226499C' }
    RBC = @{ de = 'C58561BD760'; stageDe = 'C0A79841B22' }
    HGB = @{ de = 'CCF5EA60A2D'; stageDe = 'C5CE44836F0' }
    HCT = @{ de = 'C2508770869'; stageDe = 'C392ACEAB27' }
    MCV = @{ de = 'CBD9E45005E'; stageDe = 'CDC8A16FFC0' }
    MCH = @{ de = 'C0D65B04FAD'; stageDe = 'C41280DD120' }
    MCHC = @{ de = 'C3E59478B91'; stageDe = 'CF7D8DA7F59' }
    PLT = @{ de = 'C4ADB8B437E'; stageDe = 'CCFB78E32FF' }
    NEUT = @{ de = 'C68FC2E78BE'; stageDe = 'C0208BD18F5' }
    LYM = @{ de = 'C44C79EE177'; stageDe = 'CF721840907' }
    MONO = @{ de = 'C2969857371'; stageDe = 'C1BAE382B0E' }
    EOS = @{ de = 'CE7E0C08FDC'; stageDe = 'CB36050A4E4' }
    BASO = @{ de = 'C906F7ABC83'; stageDe = 'CE036E217E9' }
}
$codes = @('ANALYZER_ID','ANALYZER_NAME','BARCODE','PATIENT_CODE','PATIENT_NAME','PATIENT_GENDER','PATIENT_AGE','WBC','RBC','HGB','HCT','MCV','MCH','MCHC','PLT','NEUT','LYM','MONO','EOS','BASO')
$names = @{
    ANALYZER_ID = 'Analyzer ID'
    ANALYZER_NAME = 'Analyzer Name'
    BARCODE = 'Barcode'
    PATIENT_CODE = 'Patient Code'
    PATIENT_NAME = 'Patient Name'
    PATIENT_GENDER = 'Gender'
    PATIENT_AGE = 'Age'
}
$dataElements = @($codes | ForEach-Object {
    @{
        id = $identifiers[$_].de
        code = "CBC_$_"
        name = if ($names.ContainsKey($_)) { $names[$_] } else { "CBC - $_" }
        shortName = if ($names.ContainsKey($_)) { $names[$_] } else { "CBC $_" }
        domainType = 'TRACKER'
        valueType = if ($_ -in @('BARCODE','ANALYZER_ID','ANALYZER_NAME','PATIENT_CODE','PATIENT_NAME','PATIENT_GENDER')) { 'TEXT' } else { 'NUMBER' }
        aggregationType = 'NONE'
    }
})
$stageElements = @($codes | ForEach-Object -Begin { $n = 0 } -Process {
    $n++
    @{ id = $identifiers[$_].stageDe; dataElement = @{ id = $identifiers[$_].de }; sortOrder = $n; compulsory = ($_ -eq 'BARCODE') }
})
$sections = @(
    @{ id = 'CBB6A37CDD3'; name = 'Analyzer'; members = @('ANALYZER_ID','ANALYZER_NAME','BARCODE') },
    @{ id = 'C823F6F34C9'; name = 'Patient Info'; members = @('PATIENT_CODE','PATIENT_NAME','PATIENT_GENDER','PATIENT_AGE') },
    @{ id = 'CAB7622B5AA'; name = 'Results'; members = @('WBC','RBC','HGB','HCT','MCV','MCH','MCHC','PLT','NEUT','LYM','MONO','EOS','BASO') }
)
$sectionObjects = @($sections | ForEach-Object -Begin { $sectionIndex = 0 } -Process {
    $section = $_
    $sectionIndex++
    @{
        id = $section.id
        name = $section.name
        sortOrder = $sectionIndex
        programStage = @{ id = $stageUid }
        dataElements = @($section.members | ForEach-Object { @{ id = $identifiers[$_].de } })
    }
})
$payload = @{
    dataElements = $dataElements
    programs = @(@{
        id = $programUid
        code = 'CBC_RESULTS'
        name = 'CBC Laboratory Results'
        shortName = 'CBC Results'
        version = $ProgramVersion
        programType = 'WITHOUT_REGISTRATION'
        organisationUnits = @(@{ id = $OrgUnitUid })
        programStages = @(@{ id = $stageUid })
    })
    programStages = @(@{
        id = $stageUid
        name = 'CBC Results'
        program = @{ id = $programUid }
        programStageDataElements = $stageElements
        programStageSections = @($sectionObjects | ForEach-Object { @{ id = $_.id } })
    })
    programStageSections = $sectionObjects
}
$json = $payload | ConvertTo-Json -Depth 20
$pair = "$($env:DHIS2_USER):$($env:DHIS2_PASSWORD)"
$authorization = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
$headers = @{ Authorization = "Basic $authorization" }
$url = $BaseUrl.TrimEnd('/') + '/api/metadata?importMode=VALIDATE&importStrategy=CREATE_AND_UPDATE&atomicMode=ALL'
$report = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType 'application/json' -Body $json

if ($report.status -ne 'OK') {
    $report | ConvertTo-Json -Depth 15
    throw 'DHIS2 metadata validation failed.'
}
if (-not $ValidateOnly) {
    $url = $url.Replace('importMode=VALIDATE', 'importMode=COMMIT')
    $report = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType 'application/json' -Body $json
    if ($report.status -ne 'OK') {
        $report | ConvertTo-Json -Depth 15
        throw 'DHIS2 metadata import failed.'
    }
}

[pscustomobject]@{
    Mode = if ($ValidateOnly) { 'VALIDATED' } else { 'CREATED_OR_UPDATED' }
    ProgramUid = $programUid
    StageUid = $stageUid
    OrgUnitUid = $OrgUnitUid
    DataElements = @($dataElements | ForEach-Object { "$($_.code):$($_.id)" }) -join ', '
}
