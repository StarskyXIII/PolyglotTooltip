# Release to CurseForge

1. Set the mod version in the loader build scripts and add `changelogs/<version>.md`.
2. Push the release commit to `main`, or run **Build** manually on `main`. This is the build-only step. It runs the Stonecutter matrix and produces one `curseforge-release` bundle containing the production JARs, the changelog, and `release.json` with the commit and SHA-256 checksums.
3. Review the successful Build run's summary and bundle. Then run **Publish to CurseForge** on `main` with that Build run ID and the desired CurseForge release type. The publish workflow checks that the run built the same commit and uploads the verified JARs with the bundled changelog. Bundles are retained for 14 days.

Set the repository variable `CURSEFORGE_PROJECT_ID` to `1485078` and the repository secret `CURSEFORGE_TOKEN` to an author API token that can upload files to the project. Only the manual publish workflow uses the token. Fabric files declare Fabric API as required.

Each target uploads as a separate CurseForge file. The publish workflow summary records the file ID and URL. If some uploads fail, check CurseForge for those files before using **Re-run failed jobs** on the same publish run.
