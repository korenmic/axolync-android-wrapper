# Axolync-Browser Submodule Setup

## Important Note

The `axolync-browser` Git submodule needs to be added manually after the initial project setup. The `.gitmodules` file has been configured to reference the axolync-browser repository at version v1.0.0.

## Adding the Submodule

Since the actual axolync-browser repository URL needs to be confirmed, you'll need to either:

### Option 1: If the repository exists at the configured URL

```bash
git submodule init
git submodule update
```

### Option 2: If you need to add the submodule manually

```bash
# Remove the .gitmodules entry if needed
# Then add the submodule with the correct URL:
git submodule add -b v1.0.0 <actual-axolync-browser-repo-url> axolync-browser
```

### Option 3: For development/testing without the actual repository

If you're developing the wrapper before the axolync-browser repository is available, you can:

1. Create a mock `axolync-browser/dist/` directory structure:
```bash
mkdir -p axolync-browser/dist
touch axolync-browser/dist/index.html
```

2. Add placeholder content to test the build process:
```bash
echo '<!DOCTYPE html><html><head><title>Axolync</title></head><body><h1>Axolync Browser Placeholder</h1></body></html>' > axolync-browser/dist/index.html
```

This will allow the Gradle build to complete and copy assets, even without the full axolync-browser implementation.

## Building axolync-browser

Once the submodule is properly initialized, build the web application:

```bash
cd axolync-browser
npm install
npm run build
cd ..
```

The build output in `axolync-browser/dist/` will be automatically copied to `app/src/main/assets/public/` during the Android build process.

## Gradle Build Integration

The `app/build.gradle.kts` file includes a custom Gradle task `copyAxolyncBrowserAssets` that:
- Copies all files from `axolync-browser/dist/` to `app/src/main/assets/public/`
- Runs automatically before the `preBuild` task
- Validates that the source directory exists before attempting to copy

If the `axolync-browser/dist/` directory doesn't exist, the build will fail with a clear error message.
