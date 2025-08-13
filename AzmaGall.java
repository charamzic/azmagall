import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AzmaGall {
    private static final String[] EXT = {"jpg", "jpeg", "png", "gif", "webp"};
    private static boolean useExternalThumbs = false;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java AzmaGall /path/to/images [\"Gallery Title\"]");
            System.out.println("Options:");
            System.out.println("  --external-thumbs  Use external tools for thumbnail generation");
            return;
        }

        List<String> argList = new ArrayList<>(Arrays.asList(args));
        if (argList.contains("--external-thumbs")) {
            useExternalThumbs = true;
            argList.remove("--external-thumbs");
        }

        if (argList.isEmpty()) {
            System.err.println("Error: No source directory specified");
            return;
        }

        Path src = Paths.get(argList.get(0));
        if (!Files.exists(src) || !Files.isDirectory(src)) {
            System.err.println("Error: source path doesn't exist or is not a directory: " + src);
            return;
        }
        String title = argList.size() >= 2 ? argList.get(1) : "My Photo Gallery";

        Path out = Paths.get("gallery");
        Path imagesOut = out.resolve("images");
        Path thumbsOut = out.resolve("thumbs");

        Files.createDirectories(imagesOut);
        Files.createDirectories(thumbsOut);

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
        if (useExternalThumbs) {
            System.out.println("Using external tools for thumbnail generation...");
        } else {
            System.out.println("Using CSS-based thumbnails (copying originals)...");
        }

        List<String> galleryItems = new ArrayList<>();
        int idx = 0;
        for (Path f : files) {
            idx++;
            String safeName = sanitizeFilename(f.getFileName().toString());
            Path dstImage = imagesOut.resolve(safeName);
            Path dstThumb = thumbsOut.resolve(safeName);

            if (!Files.exists(dstImage)) {
                Files.copy(f, dstImage, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!Files.exists(dstThumb)) {
                boolean thumbCreated = false;

                if (useExternalThumbs) {
                    thumbCreated = makeExternalThumb(dstImage, dstThumb);
                    if (thumbCreated) {
                        System.out.println("Created thumbnail using external tool: " + safeName);
                    }
                }

                if (!thumbCreated) {
                    Files.copy(dstImage, dstThumb, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            galleryItems.add(safeName);
            if (idx % 10 == 0) System.out.println("Processed " + idx + " / " + files.size());
        }

        writeResource(out.resolve("style.css"), styleCss());
        writeResource(out.resolve("script.js"), scriptJs());
        writeIndexHtml(out.resolve("index.html"), title, galleryItems);

        System.out.println("Gallery generated in: " + out.toAbsolutePath());
        System.out.println("Open " + out.resolve("index.html").toString() + " in a browser or upload the folder to a static host.");

        if (!useExternalThumbs) {
            System.out.println();
            System.out.println("Note: Using CSS-based thumbnails. For better performance with large images,");
            System.out.println("consider installing ImageMagick and using --external-thumbs option.");
        }
    }

    private static boolean hasExt(String name) {
        String l = name.toLowerCase();
        for (String e : EXT) if (l.endsWith("." + e)) return true;
        return false;
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_");
    }

    private static boolean makeExternalThumb(Path src, Path dst) {
        if (tryCommand(new String[]{
                "convert", src.toString(), "-resize", "320x320>", "-quality", "85", dst.toString()
        })) {
            return true;
        }

        if (tryCommand(new String[]{
                "ffmpeg", "-i", src.toString(), "-vf", "scale=320:320:force_original_aspect_ratio=decrease",
                "-q:v", "3", "-y", dst.toString()
        })) {
            return true;
        }

        if (tryCommand(new String[]{
                "sips", "-Z", "320", src.toString(), "--out", dst.toString()
        })) {
            return true;
        }

        return false;
    }

    private static boolean tryCommand(String[] cmd) {
        try {
            Process proc = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            int exitCode = proc.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeResource(Path where, String content) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(where)) {
            w.write(content);
        }
    }

    private static void writeIndexHtml(Path where, String title, List<String> items) throws IOException {
        StringBuilder imgs = new StringBuilder();
        for (String n : items) {
            imgs.append(String.format("    <a href=\"images/%s\" class=\"thumb\" data-full=\"images/%s\"><img src=\"thumbs/%s\" alt=\"%s\" loading=\"lazy\"></a>\n",
                    n, n, n, escapeHtml(n)));
        }

        String html = "<!doctype html>\n" +
                "<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "<title>" + escapeHtml(title) + "</title>\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n" +
                "<header><h1>" + escapeHtml(title) + "</h1></header>\n" +
                "<main class=\"grid\">\n" + imgs.toString() + "</main>\n" +
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
                ".grid{display:grid; grid-template-columns:repeat(auto-fill,minmax(140px,1fr)); gap:8px; padding:12px; position:relative; z-index:1;}" +
                ".thumb{display:block; overflow:hidden; border-radius:6px; background:#222; border:1px solid rgba(255,255,255,0.03); aspect-ratio:1; position:relative;}" +
                ".thumb img{width:100%; height:100%; object-fit:cover; display:block; transition:transform 0.2s ease;}" +
                ".thumb:hover img{transform:scale(1.05);}" +
                "#lightbox{position:fixed; inset:0; display:flex; align-items:center; justify-content:center; z-index:9999; backdrop-filter:blur(4px);}" +
                "#lightbox.hidden{display:none;}" +
                "#lb-bg{position:absolute; inset:0; background:rgba(0,0,0,0.85);}" +
                "#lb-img{max-width:95%; max-height:90%; z-index:10000; border-radius:6px; box-shadow:0 10px 30px rgba(0,0,0,0.7);}" +
                "#prev,#next{position:fixed; top:50%; transform:translateY(-50%); z-index:10001; background:rgba(0,0,0,0.5); border:2px solid rgba(255,255,255,0.3); color:#fff; font-size:24px; cursor:pointer; padding:12px 16px; border-radius:50%; transition:all 0.2s ease;}" +
                "#prev:hover,#next:hover{background:rgba(0,0,0,0.8); border-color:rgba(255,255,255,0.6);}" +
                "#prev{left:20px;} #next{right:20px;}" +
                "@media (max-width: 600px){.grid{grid-template-columns:repeat(auto-fill,minmax(100px,1fr)); gap:6px; padding:8px;} #prev{left:10px;} #next{right:10px;}}";
    }

    private static String scriptJs() {
        return "/* Minimal gallery JS */\n" +
                "(function(){\n" +
                "  const thumbs = Array.from(document.querySelectorAll('.thumb'));\n" +
                "  const lb = document.getElementById('lightbox');\n" +
                "  const lbImg = document.getElementById('lb-img');\n" +
                "  const prevBtn = document.getElementById('prev');\n" +
                "  const nextBtn = document.getElementById('next');\n" +
                "  let idx = -1;\n" +
                "  function openAt(i){ idx = (i+thumbs.length)%thumbs.length; lbImg.src = thumbs[idx].dataset.full; lb.classList.remove('hidden'); document.body.style.overflow='hidden'; }\n" +
                "  function closeLb(){ lb.classList.add('hidden'); lbImg.src=''; document.body.style.overflow=''; }\n" +
                "  function next(){ openAt(idx+1); }\n" +
                "  function prev(){ openAt(idx-1); }\n" +
                "  thumbs.forEach((t,i)=> t.addEventListener('click', e=>{ e.preventDefault(); openAt(i); }));\n" +
                "  document.getElementById('lb-bg').addEventListener('click', closeLb);\n" +
                "  nextBtn.addEventListener('click', e=>{ e.stopPropagation(); next(); });\n" +
                "  prevBtn.addEventListener('click', e=>{ e.stopPropagation(); prev(); });\n" +
                "  document.addEventListener('keydown', e=>{ if (lb.classList.contains('hidden')) return; if (e.key==='ArrowRight'||e.key===' ') next(); if (e.key==='ArrowLeft') prev(); if (e.key==='Escape') closeLb(); });\n" +
                "  // touch swipe\n" +
                "  let startX=0;\n" +
                "  lbImg.addEventListener('touchstart', e=>{ startX = e.touches[0].clientX; });\n" +
                "  lbImg.addEventListener('touchend', e=>{ let dx = (e.changedTouches[0].clientX - startX); if (dx>30) prev(); else if (dx<-30) next(); });\n" +
                "})();";
    }
}