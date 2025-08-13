# AzmaGall 📸

Simple, fast static photo gallery generator. Creates responsive galleries from your image folders with zero dependencies.

## ✨ Features

- **Zero dependencies** - Single executable file, no Java runtime needed
- **Fast thumbnails** - CSS-based resizing or external tools (ImageMagick/FFmpeg)
- **Responsive design** - Works great on desktop and mobile
- **Lightbox viewer** - Keyboard navigation, touch swipe, zoom
- **Dark theme** - Easy on the eyes
- **Lazy loading** - Fast page loads even with many images

## 🚀 Quick Start

### Download

Go to [Releases](../../releases) and download the binary for your platform:

- **macOS (Apple Silicon)**: `azmagall-macos-aarch64`
- **Linux (x64)**: `azmagall-linux-x64`

### macOS Setup

```bash
# Download and make executable
chmod +x azmagall-macos-aarch64

# Remove quarantine (required for unsigned binaries)
xattr -d com.apple.quarantine azmagall-macos-aarch64

# Create gallery
./azmagall-macos-aarch64 /path/to/photos "My Gallery"
```

**Note**: macOS may show "cannot be opened because it is from an unidentified developer". This is normal for unsigned binaries. The `xattr` command fixes this.

### Linux Setup

```bash
# Download and make executable
chmod +x azmagall-linux-x64

# Create gallery
./azmagall-linux-x64 /path/to/photos "My Gallery"
```

## 📖 Usage

### Basic Usage

```bash
azmagall /path/to/images "Gallery Title"
```

This will:
1. Scan `/path/to/images` for supported formats (JPG, PNG, GIF, WebP)
2. Create a `gallery/` folder in current directory
3. Copy images and generate thumbnails
4. Create `index.html` with responsive gallery

### Advanced Usage

#### External Thumbnail Generation

For better quality thumbnails and smaller file sizes:

```bash
azmagall --external-thumbs /path/to/images "Gallery Title"
```

This tries to use external tools in order of preference:
1. **ImageMagick** (`convert`) - Best quality, most formats
2. **FFmpeg** (`ffmpeg`) - Good for video thumbnails too
3. **sips** (macOS only) - Built-in macOS tool

**Installing external tools:**

**macOS:**
```bash
brew install imagemagick
# or
brew install ffmpeg
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt install imagemagick
# or
sudo apt install ffmpeg
```