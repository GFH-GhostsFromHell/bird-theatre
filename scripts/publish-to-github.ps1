# Publish the Bird Theatre source to the owner's GitHub account.
# Requires authenticated GitHub CLI (gh) and Git; does not overwrite remote history.
[CmdletBinding()]
param(
    [ValidateSet('public', 'private')]
    [string] $Visibility = 'public'
)

$ErrorActionPreference = 'Stop'
$repo = 'GFH-GhostsFromHell/bird-theatre'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

foreach ($tool in @('git', 'gh')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "Missing '$tool'. Install Git for Windows and GitHub CLI before running this script."
    }
}

& gh auth status
if ($LASTEXITCODE -ne 0) {
    throw 'GitHub CLI is not signed in. Run: gh auth login'
}

if (-not (Test-Path (Join-Path $root '.git'))) {
    & git -C $root init -b main
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize Git repository.' }
}

# Use an existing Git identity if present; otherwise configure the signed-in account locally.
$name = (& git -C $root config user.name)
$email = (& git -C $root config user.email)
if ([string]::IsNullOrWhiteSpace($name) -or [string]::IsNullOrWhiteSpace($email)) {
    $login = (& gh api user --jq '.login').Trim()
    $id = (& gh api user --jq '.id').Trim()
    if ($LASTEXITCODE -ne 0 -or -not $login -or -not $id) { throw 'Could not determine GitHub author information.' }
    & git -C $root config user.name $login
    & git -C $root config user.email "$id+$login@users.noreply.github.com"
}

& git -C $root add -A
if ($LASTEXITCODE -ne 0) { throw 'Could not stage Bird Theatre files.' }
& git -C $root diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
    & git -C $root commit -m 'Add Bird Theatre cross-platform apps, recognition, RFID, and Windows updater'
    if ($LASTEXITCODE -ne 0) { throw 'Could not commit project files.' }
} elseif ($LASTEXITCODE -ne 0) {
    throw 'Failed to check staged changes.'
}

$branch = (& git -C $root branch --show-current).Trim()
if ($branch -ne 'main') {
    throw "The current branch is '$branch', not 'main'. Refusing to rename or overwrite existing branch history."
}

# If gh can see the repository, push to it; otherwise ask gh to create it.
& gh repo view $repo --json nameWithOwner --jq '.nameWithOwner' 2>$null | Out-Null
$exists = $LASTEXITCODE -eq 0

if ($exists) {
    $remote = (& git -C $root remote get-url origin 2>$null)
    if ($LASTEXITCODE -eq 0) {
        if ($remote -notmatch '(^https://github\.com/|^git@github\.com:|^ssh://git@github\.com/|^http://github\.com/)GFH-GhostsFromHell/bird-theatre(\.git)?$') {
            throw "An origin remote already exists and points somewhere else: $remote"
        }
    } else {
        & git -C $root remote add origin "https://github.com/$repo.git"
        if ($LASTEXITCODE -ne 0) { throw 'Could not configure GitHub remote.' }
    }
    & git -C $root push -u origin main
    if ($LASTEXITCODE -ne 0) {
        throw 'GitHub rejected the push. If the online repository already has its own README or commits, reconcile histories first; do not force push.'
    }
} else {
    $flag = if ($Visibility -eq 'public') { '--public' } else { '--private' }
    Write-Host "Creating $Visibility repository $repo and pushing Bird Theatre files..."
    & gh repo create $repo $flag --source $root --remote origin --push
    if ($LASTEXITCODE -ne 0) {
        throw 'GitHub repository creation or push failed. Check permissions and whether the repository already exists.'
    }
}

Write-Host "Published to https://github.com/$repo" -ForegroundColor Green
Write-Host 'Windows installer workflow: https://github.com/GFH-GhostsFromHell/bird-theatre/actions/workflows/build-desktop.yml'
Write-Host 'For automatic Windows updates, configure the signing keys in the repository as documented.'
