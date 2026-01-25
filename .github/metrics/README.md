# Build Metrics

This directory stores build metrics generated during CI workflow runs.

## Files

- `error_count.txt` - Count of build errors (initialized to 0)
- `warning_count.txt` - Count of build warnings (initialized to 0)

## Purpose

These files are used by the GitHub Actions workflow to:
1. Track build errors and warnings across build steps
2. Determine workflow success/failure based on error count
3. Generate build summary reports

## Notes

- Files are auto-generated during workflow execution
- Directory is created automatically if it doesn't exist
- Files are initialized to "0" before each build
- Only actual build errors (not R8 metadata warnings) increment the error count
