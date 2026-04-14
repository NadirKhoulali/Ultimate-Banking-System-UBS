# UBS Release Checklist

Use this checklist before publishing a new build to CurseForge/GitHub.

## 1) Versioning
- Update `gradle.properties`:
  - `mod_version`
- Verify API version string in:
  - `src/main/java/net/austizz/ultimatebankingsystem/api/UltimateBankingApiImpl.java`
- Verify docs mention the current version:
  - `docs/wiki/Developer-Integration-Tutorial.md`

## 2) Metadata
- Verify `src/main/templates/META-INF/neoforge.mods.toml`:
  - `displayName`
  - `version`
  - `issueTrackerURL`
  - `displayURL`
  - `authors`
  - `description`
- Ensure license is correct (`gradle.properties` -> `mod_license`).

## 3) Documentation
- Update `README.md` feature/command/API highlights.
- Update `CHANGELOG.md` with new release section.
- Update wiki source pages in `docs/wiki/` if behavior/commands/config changed.
- Prepare release page text from:
  - `docs/release/CURSEFORGE_DESCRIPTION_WYSIWYG.html`

## 4) Validation
- Run compile:
  - `./gradlew -q compileJava`
- Run build:
  - `./gradlew build`
- Smoke test in client:
  - ATM open flow
  - PIN/account switching
  - withdraw/deposit/transfer/pay request
  - bank-owner PC app launch + core actions

## 5) Publishing
- Commit with a release message.
- Tag release in git (`vX.Y.Z`).
- Upload JAR to CurseForge.
- Paste/update long description in CurseForge using the WYSIWYG source file.
- Push wiki updates (if changed) to repo wiki branch.

