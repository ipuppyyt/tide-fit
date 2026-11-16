# Security policy

## Reporting a vulnerability
Please report security problems privately through GitHub: **Security tab > Report a vulnerability** on this repository. Do not open a public issue.

Include what you found, steps to reproduce, and the affected version. You can expect an acknowledgement within a week.

## Scope
TIDE Fit stores everything on the device and has no network access, so the main areas of interest are local data handling (database, backups and restore), the Health Connect integration, the JSON import used for coaching, and notification and widget intents.

Known limitation: backup files are not encrypted.

## Supported versions
Only the latest release receives fixes.
