# Splash Screen Images

## Overview
The splash screen uses orientation-specific images for optimal display on different device orientations.

## Image Specifications

### Portrait Mode (Tall Devices)
- **File**: `drawable-port/splash_logo_portrait.png`
- **Recommended size**: 1080x1920 pixels (or higher resolution maintaining aspect ratio)
- **Aspect ratio**: 9:16 (portrait)
- **Format**: PNG with transparency
- **Usage**: Displayed on portrait-oriented devices

### Landscape Mode (Wide Devices)
- **File**: `drawable-land/splash_logo_landscape.png`
- **Recommended size**: 1920x1080 pixels (or higher resolution maintaining aspect ratio)
- **Aspect ratio**: 16:9 (landscape)
- **Format**: PNG with transparency
- **Usage**: Displayed on landscape-oriented devices

### Default/Fallback
- **File**: `drawable/splash_logo.xml` (currently references axolync_logo.png)
- **Usage**: Used if orientation-specific images are not provided

## How to Add Custom Splash Images

1. **For Portrait Mode**:
   - Place your high-resolution portrait splash image at:
     `app/src/main/res/drawable-port/splash_logo_portrait.png`
   - Update `splash_logo.xml` to reference it for portrait orientation

2. **For Landscape Mode**:
   - Place your high-resolution landscape splash image at:
     `app/src/main/res/drawable-land/splash_logo_landscape.png`
   - Update `splash_logo.xml` to reference it for landscape orientation

3. **Update splash_logo.xml**:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
       <item>
           <bitmap
               android:src="@drawable/splash_logo_portrait"
               android:gravity="center" />
       </item>
   </layer-list>
   ```

## Current Configuration

- **Background Color**: Dark gray (#1A1A1A) - defined in `values/colors.xml` as `splash_background`
- **Minimum Display Duration**: 2 seconds (enforced in MainActivity)
- **Animation Duration**: 200ms fade-in
- **Theme**: `Theme.App.Starting` in `values/themes.xml`

## Testing

After adding new splash images:
1. Clean and rebuild: `./gradlew clean :app:assembleDebug`
2. Install on device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Launch app and verify splash screen displays correctly in both orientations
