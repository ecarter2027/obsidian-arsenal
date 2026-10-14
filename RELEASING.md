# Releases

- Every build made with `build.bat` gets the next patch version (1.0.9, 1.0.10, ...).
- **Every version is released on GitHub.** After each build its jar is committed to `releases/` with a short note in
  `releases/notes/<version>.md`, and the source on `main` is updated.
- To publish: Actions tab -> **Publish releases** -> Run workflow. It creates a release for each jar in `releases/`
  that doesn't have one yet and marks the newest as latest.
