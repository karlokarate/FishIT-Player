# Release Deletion Guide

This guide explains how to delete all GitHub releases from the FishIT-Player repository to save space.

## Quick Start

**TL;DR** - To delete all releases immediately:

```bash
# Install GitHub CLI if needed
# https://cli.github.com/

# Authenticate
gh auth login

# Delete all releases (no confirmation)
./scripts/delete-releases-oneliner.sh
```

Or for a safer approach with confirmation:

```bash
./scripts/delete-all-releases.sh
```

## Why Delete Releases?

The repository currently has multiple releases containing APK files (30-37 MB each). Deleting these releases will free up significant storage space.

## Current Releases

As of the last check, the repository contains the following releases:
- dragon (2 APKs + checksums)
- zoolander (2 APKs + checksums)
- uu (2 APKs + checksums)
- optimum (2 APKs + checksums)
- hdhd (2 APKs + checksums)
- silverbear (2 APKs + checksums)
- goldtwo (2 APKs + checksums)
- goldone (2 APKs + checksums)
- fff (2 APKs + checksums)
- Jupiterone (2 APKs + checksums)
- quessel (2 APKs + checksums)
- goldbeta (2 APKs + checksums)
- betaone (2 APKs + checksums)
- boringssl-28efc83e86dc-android24-ndk-r27c (5 assets)
- v0.2.0 (no assets)

## Deletion Methods

### Method 1: Using the Automated Script (Recommended)

This is the safest method with dry-run support and confirmation prompts.

1. Ensure you have the [GitHub CLI](https://cli.github.com/) installed
2. Authenticate with GitHub:
   ```bash
   gh auth login
   ```
3. Run a dry run first to see what would be deleted:
   ```bash
   ./scripts/delete-all-releases.sh --dry-run
   ```
4. Delete all releases:
   ```bash
   ./scripts/delete-all-releases.sh
   ```
5. Confirm when prompted (type `yes`)

### Method 2: Using the One-Liner Script (Fast)

For quick deletion without prompts:

1. Ensure GitHub CLI is installed and authenticated
2. Run the script:
   ```bash
   ./scripts/delete-releases-oneliner.sh
   ```

This will immediately delete all releases without confirmation.

### Method 3: Using GitHub CLI Manually

Delete each release individually:

```bash
gh release delete dragon --repo karlokarate/FishIT-Player --yes
gh release delete zoolander --repo karlokarate/FishIT-Player --yes
gh release delete uu --repo karlokarate/FishIT-Player --yes
gh release delete optimum --repo karlokarate/FishIT-Player --yes
gh release delete hdhd --repo karlokarate/FishIT-Player --yes
gh release delete silverbear --repo karlokarate/FishIT-Player --yes
gh release delete goldtwo --repo karlokarate/FishIT-Player --yes
gh release delete goldone --repo karlokarate/FishIT-Player --yes
gh release delete fff --repo karlokarate/FishIT-Player --yes
gh release delete Jupiterone --repo karlokarate/FishIT-Player --yes
gh release delete quessel --repo karlokarate/FishIT-Player --yes
gh release delete goldbeta --repo karlokarate/FishIT-Player --yes
gh release delete betaone --repo karlokarate/FishIT-Player --yes
gh release delete boringssl-28efc83e86dc-android24-ndk-r27c --repo karlokarate/FishIT-Player --yes
gh release delete v0.2.0 --repo karlokarate/FishIT-Player --yes
```

### Method 3: Using GitHub CLI Manually

Delete each release individually:

```bash
gh release delete dragon --repo karlokarate/FishIT-Player --yes
gh release delete zoolander --repo karlokarate/FishIT-Player --yes
gh release delete uu --repo karlokarate/FishIT-Player --yes
gh release delete optimum --repo karlokarate/FishIT-Player --yes
gh release delete hdhd --repo karlokarate/FishIT-Player --yes
gh release delete silverbear --repo karlokarate/FishIT-Player --yes
gh release delete goldtwo --repo karlokarate/FishIT-Player --yes
gh release delete goldone --repo karlokarate/FishIT-Player --yes
gh release delete fff --repo karlokarate/FishIT-Player --yes
gh release delete Jupiterone --repo karlokarate/FishIT-Player --yes
gh release delete quessel --repo karlokarate/FishIT-Player --yes
gh release delete goldbeta --repo karlokarate/FishIT-Player --yes
gh release delete betaone --repo karlokarate/FishIT-Player --yes
gh release delete boringssl-28efc83e86dc-android24-ndk-r27c --repo karlokarate/FishIT-Player --yes
gh release delete v0.2.0 --repo karlokarate/FishIT-Player --yes
```

### Method 4: Using GitHub Web Interface

1. Go to https://github.com/karlokarate/FishIT-Player/releases
2. For each release:
   - Click on the release
   - Click the "Delete" button
   - Confirm the deletion

## Preventing Future Releases

To prevent automatic release creation, you can:

1. Disable the release build workflow:
   - Go to `.github/workflows/release-build.yml`
   - Set the workflow to only run manually
   
2. Or remove release creation from the workflow:
   - Edit `.github/workflows/release-build.yml`
   - Remove or disable the `create-release` job

## Verification

After deletion, verify all releases are removed:

```bash
gh release list --repo karlokarate/FishIT-Player
```

This should return an empty list or "no releases found".

## Note

Deleting releases does not delete git tags. If you also want to delete the tags:

```bash
git tag -l | xargs -n 1 git push --delete origin
git tag -l | xargs git tag -d
```

**Warning**: Only delete tags if you're sure you don't need them for versioning purposes.
