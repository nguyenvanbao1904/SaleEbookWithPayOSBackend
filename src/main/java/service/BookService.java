package service;

import com.google.gson.Gson;
import model.Book;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class BookService {
    private final Gson gson = new Gson();

    // Đường dẫn tới JSON ngoài resources
    private final String DATA_PATH = "data/ebook-data.json";
    private final String PDF_PATH = "data/ebook.pdf";

    // Đọc thông tin sách
    public Book readBook() throws IOException {
        Path path = Paths.get(DATA_PATH);
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "{}", StandardCharsets.UTF_8);
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return gson.fromJson(json, Book.class);
    }

    // Cập nhật thông tin sách
    public void updateBook(Book book) throws IOException {
        Path path = Paths.get(DATA_PATH);
        Files.createDirectories(path.getParent());
        try (FileWriter writer = new FileWriter(path.toFile())) {
            gson.toJson(book, writer);
        }
    }

    // Lấy byte của PDF để serve download
    public byte[] getPdfBytes() throws IOException {
        Path path = Paths.get(PDF_PATH);
        if (!Files.exists(path)) {
            throw new IOException("PDF file not found: " + PDF_PATH);
        }
        return Files.readAllBytes(path);
    }
}
