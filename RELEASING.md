# Release cadence

- Every build made with `build.bat` gets the next patch version (1.0.8, 1.0.9, ...).
- **Only every 5th version goes to GitHub, counting from 1.0.8:** 1.0.8, 1.0.13, 1.0.18, 1.0.23, ...
  Those jars are committed to `releases/` with a short note in `releases/notes/<version>.md`; the source on `main`
  is updated with every version regardless.
- To publish: Actions tab -> **Publish releases** -> Run workflow. It creates a release for each jar in
  `releases/` that doesn't have one yet and marks the newest as latest.
- 1.0.0 - 1.0.7 were all published before this rule; 1.0.8 starts the cadence.
