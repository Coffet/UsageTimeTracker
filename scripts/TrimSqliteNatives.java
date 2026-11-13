import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class TrimSqliteNatives {
    private static final String NATIVE_ROOT = "org/sqlite/native/";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the package lib directory");
        }
        Path lib = Path.of(args[0]);
        List<Path> jars;
        try (var files = Files.list(lib)) {
            jars = files.filter(path -> path.getFileName().toString().matches("sqlite-jdbc-.*\\.jar"))
                    .toList();
        }
        if (jars.size() != 1) {
            throw new IOException("Expected exactly one sqlite-jdbc jar in " + lib);
        }

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String sqliteArch = switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "x86", "i386", "i686" -> "x86";
            default -> throw new IOException("Unsupported SQLite packaging architecture: " + arch);
        };
        Set<String> nativeFolders;
        if (os.startsWith("windows")) {
            nativeFolders = Set.of("Windows/" + sqliteArch);
        } else if (os.startsWith("mac") || os.startsWith("darwin")) {
            nativeFolders = Set.of("Mac/" + sqliteArch);
        } else if (os.startsWith("linux")) {
            nativeFolders = Set.of("Linux/" + sqliteArch, "Linux-Musl/" + sqliteArch);
        } else {
            throw new IOException("Unsupported SQLite packaging OS: " + os);
        }

        Path jar = jars.get(0);
        Path temporary = Files.createTempFile(lib, "sqlite-trim-", ".jar");
        try {
            try (ZipFile source = new ZipFile(jar.toFile());
                 ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                Set<String> foundFolders = new HashSet<>();
                var entries = source.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    String upper = name.toUpperCase(Locale.ROOT);
                    if (upper.startsWith("META-INF/") &&
                            (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"))) {
                        throw new IOException("Cannot trim a signed SQLite jar: " + jar);
                    }
                    if (name.startsWith(NATIVE_ROOT) && !entry.isDirectory()) {
                        String nativePath = name.substring(NATIVE_ROOT.length());
                        String folder = nativeFolders.stream()
                                .filter(candidate -> nativePath.startsWith(candidate + "/"))
                                .findFirst().orElse(null);
                        if (folder == null) {
                            continue;
                        }
                        foundFolders.add(folder);
                    }
                    ZipEntry copy = new ZipEntry(name);
                    copy.setTime(entry.getTime());
                    output.putNextEntry(copy);
                    if (!entry.isDirectory()) {
                        try (InputStream input = source.getInputStream(entry)) {
                            input.transferTo(output);
                        }
                    }
                    output.closeEntry();
                }
                if (!foundFolders.containsAll(nativeFolders)) {
                    throw new IOException("Missing SQLite native library for " + nativeFolders);
                }
            }
            Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
