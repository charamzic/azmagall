/*
AzmaGall - simple static photo gallery generator
Author: ChatGPT (for Jan)

Usage:
  java AzmaGall /path/to/images "Gallery Title"

What it does:
  - Scans provided directory for image files (jpg, jpeg, png)
  - Creates output directory `gallery/` in the current working dir
    with subfolders `images/` (copied originals) and `thumbs/` (generated)
  - Produces index.html, style.css, script.js used as a minimal responsive gallery
  - Thumbnails are generated with max width 320px (keeps aspect ratio)

Build (simple):
  javac AzmaGall.java
  jar cfe azmagall.jar AzmaGall AzmaGall.class

Build native (GraalVM native-image):
  # install GraalVM + native-image (follow Graal docs)
  native-image --no-fallback -jar azmagall.jar -H:Name=azmagall
  # then run: ./azmagall /path/to/images "My Gallery"

Notes & best practices (opinionated):
  - Keep code small and dependency-free for easy native-image builds.
  - Avoid loading huge images fully into memory when possible; for very large inputs
    you may want streaming or native tools (ImageMagick) instead.
  - Always sanitize/validate user input paths in production.
  - This generator is intentionally minimal — easy to extend (captions, EXIF, sorting).

*/

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public class AzmaGall {
    private static final int THUMB_WIDTH = 320;
    private static final String[] EXT = {"jpg", "jpeg", "png"};

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java AzmaGall /path/to/images [\"Gallery Title\"]");
            return;
        }
        Path src = Paths.get(args[0]);
        if (!Files.exists(src) || !Files.isDirectory(src)) {
            System.err.println("Error: source path doesn't exist or is not a directory: " + src);
            return;
        }
        String title = args.length >= 2 ? args[1] : "My Photo Gallery";

        Path out = Paths.get("gallery");
        Path imagesOut = out.resolve("images");
        Path thumbsOut = out.resolve("thumbs");

        // Create output dirs
        Files.createDirectories(imagesOut);
        Files.createDirectories(thumbsOut);

        // Gather image files
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(src)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p) && hasExt(p.getFileName().toString())) {
                    files.add(p);
                }
            }
        }
        if (files.isEmpty()) {
            System.out.println("No supported images found in " + src);
            return;
        }

        files.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));

        System.out.println("Found " + files.size() + " images. Processing...");

        List<String> galleryItems = new ArrayList<>();
        int idx = 0;
        for (Path f : files) {
            idx++;
            String safeName = sanitizeFilename(f.getFileName().toString());
            Path dstImage = imagesOut.resolve(safeName);
            Path dstThumb = thumbsOut.resolve(safeName);

            // copy original if not exists
            if (!Files.exists(dstImage)) {
                Files.copy(f, dstImage, StandardCopyOption.REPLACE_EXISTING);
            }

            // make thumbnail
            if (!Files.exists(dstThumb)) {
                try {
                    makeThumb(dstImage.toFile(), dstThumb.toFile(), THUMB_WIDTH);
                } catch (Exception e) {
                    System.err.println("Failed to make thumb for " + f + ": " + e.getMessage());
                    // fallback: copy original as thumb
                    Files.copy(dstImage, dstThumb, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            galleryItems.add(safeName);
            if (idx % 10 == 0) System.out.println("Processed " + idx + " / " + files.size());
        }

        // write static assets
        writeResource(out.resolve("style.css"), styleCss());
        writeResource(out.resolve("script.js"), scriptJs());
        writeIndexHtml(out.resolve("index.html"), title, galleryItems);

        System.out.println("Gallery generated in: " + out.toAbsolutePath());
        System.out.println("Open " + out.resolve("index.html").toString() + " in a browser or upload the folder to a static host.");
    }

    private static boolean hasExt(String name) {
        String l = name.toLowerCase();
        for (String e : EXT) if (l.endsWith("." + e)) return true;
        return false;
    }

    private static String sanitizeFilename(String name) {
        // basic sanitization: remove path separators and control chars
        return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_");
    }

    private static void makeThumb(File src, File dst, int width) throws IOException {
        BufferedImage img = ImageIO.read(src);
        if (img == null) throw new IOException("Unsupported image format: " + src.getName());
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= width) {
            // just copy
            ImageIO.write(img, extForName(dst.getName()), dst);
            return;
        }
        double ratio = (double) width / (double) w;
        int nh = (int) Math.round(h * ratio);

        Image tmp = img.getScaledInstance(width, nh, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        ImageIO.write(resized, extForName(dst.getName()), dst);
    }

    private static String extForName(String name) {
        String l = name.toLowerCase();
        for (String e : EXT) if (l.endsWith("." + e)) return e.equals("jpg") ? "jpg" : e;
        // default
        return "jpg";
    }

    private static void writeResource(Path where, String content) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(where)) {
            w.write(content);
        }
    }

    private static void writeIndexHtml(Path where, String title, List<String> items) throws IOException {
        StringBuilder imgs = new StringBuilder();
        for (String n : items) {
            imgs.append(String.format("    <a href=\"images/%s\" class=\"thumb\" data-full=\"images/%s\"><img src=\"thumbs/%s\" alt=\"%s\"></a>\n",
                n, n, n, escapeHtml(n)));
        }

        String html = "<!doctype html>\n" +
                "<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "<title>" + escapeHtml(title) + "</title>\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n" +
                "<header><h1>" + escapeHtml(title) + "</h1></header>\n" +
                "<main class=\"grid\">\n" + imgs.toString() + "</main>\n" +
                // lightbox container
                "<div id=\"lightbox\" class=\"hidden\">\n<div id=\"lb-bg\"></div>\n<button id=\"prev\">◀</button><img id=\"lb-img\" src=\"\"><button id=\"next\">▶</button>\n</div>\n" +
                "<script src=\"script.js\"></script>\n</body>\n</html>";

        writeResource(where, html);
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String styleCss() {
        return "/* Minimal gallery CSS - responsive grid and lightbox */" +
               "body{font-family: system-ui, -apple-system, Roboto, 'Segoe UI', sans-serif; margin:0; background:#111; color:#eee;}" +
               "header{padding:12px 16px; text-align:center; background:#0d0d0d; box-shadow:0 1px 4px rgba(0,0,0,0.6);}" +
               "h1{margin:0; font-size:1.1rem;}" +
               ".grid{display:grid; grid-template-columns:repeat(auto-fill,minmax(140px,1fr)); gap:8px; padding:12px;}" +
               ".thumb{display:block; overflow:hidden; border-radius:6px; background:#222; border:1px solid rgba(255,255,255,0.03);}" +
               ".thumb img{width:100%; height:100%; object-fit:cover; display:block;}" +
               "#lightbox{position:fixed; inset:0; display:flex; align-items:center; justify-content:center; z-index:9999;}" +
               "#lb-bg{position:absolute; inset:0; background:rgba(0,0,0,0.85);}" +
               "#lb-img{max-width:95%; max-height:90%; z-index:10000; border-radius:6px; box-shadow:0 10px 30px rgba(0,0,0,0.7);}" +
               "#prev,#next{position:fixed; top:50%; transform:translateY(-50%); z-index:10001; background:transparent; border:none; color:#fff; font-size:32px; cursor:pointer; padding:10px;}" +
               "#prev{left:10px;} #next{right:10px;} .hidden{display:none;}";
    }

    private static String scriptJs() {
        // Lightweight JS: keyboard nav, click, touch swipe
        return "/* Minimal gallery JS */\n" +
                "(function(){\n" +
                "  const thumbs = Array.from(document.querySelectorAll('.thumb'));\n" +
                "  const lb = document.getElementById('lightbox');\n" +
                "  const lbImg = document.getElementById('lb-img');\n" +
                "  const prevBtn = document.getElementById('prev');\n" +
                "  const nextBtn = document.getElementById('next');\n" +
                "  let idx = -1;\n" +
                "  function openAt(i){ idx = (i+thumbs.length)%thumbs.length; lbImg.src = thumbs[idx].dataset.full; lb.classList.remove('hidden'); }\n" +
                "  function closeLb(){ lb.classList.add('hidden'); lbImg.src=''; }\n" +
                "  function next(){ openAt(idx+1); }\n" +
                "  function prev(){ openAt(idx-1); }\n" +
                "  thumbs.forEach((t,i)=> t.addEventListener('click', e=>{ e.preventDefault(); openAt(i); }));\n" +
                "  document.getElementById('lb-bg').addEventListener('click', closeLb);\n" +
                "  nextBtn.addEventListener('click', e=>{ e.stopPropagation(); next(); });\n" +
                "  prevBtn.addEventListener('click', e=>{ e.stopPropagation(); prev(); });\n" +
                "  document.addEventListener('keydown', e=>{ if (lb.classList.contains('hidden')) return; if (e.key==='ArrowRight') next(); if (e.key==='ArrowLeft') prev(); if (e.key==='Escape') closeLb(); });\n" +
                "  // touch swipe\n" +
                "  let startX=0;\n" +
                "  lbImg.addEventListener('touchstart', e=>{ startX = e.touches[0].clientX; });\n" +
                "  lbImg.addEventListener('touchend', e=>{ let dx = (e.changedTouches[0].clientX - startX); if (dx>30) prev(); else if (dx<-30) next(); });\n" +
                "})();";
    }
}
