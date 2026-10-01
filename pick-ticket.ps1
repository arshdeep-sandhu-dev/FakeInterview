# Prints a random bug ticket. Pass a ticket id to pick a specific one: .\pick-ticket.ps1 SUP-104
param([string]$Id)

$tickets = Get-ChildItem (Join-Path $PSScriptRoot "tickets") -Filter "SUP-*.md"
$pick = if ($Id) { $tickets | Where-Object { $_.BaseName -eq $Id } } else { $tickets | Get-Random }
if (-not $pick) { Write-Error "No ticket named $Id"; exit 1 }

Write-Host ""
Write-Host "==================== YOUR TICKET: $($pick.BaseName) ====================" -ForegroundColor Cyan
Write-Host ""
Get-Content $pick.FullName
Write-Host ""
Write-Host "Start your timer. Open your agent in .\shipfast and talk out loud." -ForegroundColor Cyan
