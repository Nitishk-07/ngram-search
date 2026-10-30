package ngramsearch.index;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Loads every .txt file under a directory (recursively), sorted by relative path. */
public final class DocumentLoader {

    private DocumentLoader() {
    }

    public static List<Document> loadDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted() // sorted paths -> stable doc ids -> deterministic results
                    .toList();
        }
        List<Document> docs = new ArrayList<>(files.size());
        for (Path file : files) {
            String name = dir.relativize(file).toString().replace('\\', '/');
            docs.add(new Document(name, read(file)));
        }
        return docs;
    }

    private static String read(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            // Not valid UTF-8 (e.g. an old Windows-1252 file). Latin-1 never fails to decode.
            return Files.readString(file, StandardCharsets.ISO_8859_1);
        }
    }
}
