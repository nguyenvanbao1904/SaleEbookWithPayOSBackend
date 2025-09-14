import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.Book;
import service.BookService;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

public class Server {
    public static void main(String[] args) {
        port(3030);
        BookService bookService = new BookService();

        String clientId = System.getenv("PAYOS_CLIENT_ID");
        String apiKey = System.getenv("PAYOS_API_KEY");
        String checksumKey = System.getenv("PAYOS_CHECKSUM_KEY");
        PayOS payOS = new PayOS(clientId, apiKey, checksumKey);

        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "http://localhost:3000");
            res.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type,Authorization");
        });

        post("/create-payment-link", (req, res) -> {
            Long orderCode = System.currentTimeMillis() / 1000;
            int quantity = Integer.parseInt(req.queryParams("quantity"));
            int price = Integer.parseInt(req.queryParams("price"));

            ItemData item = ItemData.builder()
                    .name(req.queryParams("name"))
                    .quantity(quantity)
                    .price(price)
                    .build();

            PaymentData payment = PaymentData.builder()
                    .orderCode(orderCode)
                    .amount(quantity * price)
                    .description("Thanh toán đơn hàng")
                    .returnUrl("http://localhost:3000/success.html")
                    .cancelUrl("http://localhost:3000/cancel.html")
                    .item(item)
                    .build();

            CheckoutResponseData result = payOS.createPaymentLink(payment);
            res.redirect(result.getCheckoutUrl(), 303);
            return "";
        });

        // Trang admin
        get("/admin", (req, res) -> {
            Book book = bookService.readBook();
            return new ModelAndView(Map.of("book", book), "admin-page.mustache");
        }, new MustacheTemplateEngine());

        // Cập nhật sách
        post("/admin/update", (req, res) -> {
            Book book = new Book(
                    req.queryParams("name"),
                    req.queryParams("description"),
                    Double.parseDouble(req.queryParams("price")),
                    req.queryParams("image")
            );
            bookService.updateBook(book);
            return "<h1>Update Success</h1><a href='/admin'>Go back</a>";
        });

        // Lấy dữ liệu sách dạng JSON
        get("/book-data", (req, res) -> {
            res.type("application/json");
            Book book = bookService.readBook();
            return new Gson().toJson(book);
        });


        // Download PDF, chỉ khi thanh toán thành công
        get("/download", (req, res) -> {
            String orderId = req.queryParams("orderId");
            if(orderId == null) {
                res.status(400);
                return "Missing orderId";
            }

            Gson gson = new Gson();
            Path path = Paths.get("data/payments.json");
            Map<String, Boolean> paymentMap = new HashMap<>();

            if(Files.exists(path)) {
                Type type = new TypeToken<Map<String, Boolean>>(){}.getType();
                paymentMap = gson.fromJson(Files.readString(path), type);
            }

            boolean isPaid = paymentMap.getOrDefault(orderId, false);

            // Nếu chưa thanh toán trong JSON → fallback gọi PayOS
            if(!isPaid) {
                var paymentInfo = payOS.getPaymentLinkInformation(Long.parseLong(orderId));
                if("PAID".equals(paymentInfo.getStatus()))
                {
                    isPaid = true;
                    paymentMap.put(orderId, true);
                    Files.writeString(path, gson.toJson(paymentMap)); // update JSON
                }
            }

            if(!isPaid) {
                res.status(403);
                return "Bạn chưa thanh toán, không thể tải PDF";
            }

            // Trả file PDF
            res.type("application/pdf");
            res.header("Content-Disposition", "attachment; filename=ebook.pdf");

            byte[] pdfBytes;
            try {
                pdfBytes = bookService.getPdfBytes();
            } catch (IOException e) {
                res.status(500);
                return "PDF file not found";
            }

            try (var out = res.raw().getOutputStream()) {
                out.write(pdfBytes);
                out.flush();
            }

            return null;
        });

        post("/webhook", (req, res) -> {
            Gson gson = new Gson();
            Map<String, Boolean> paymentMap;

            Path path = Paths.get("data/payments.json");
            if(Files.exists(path)) {
                Type type = new TypeToken<Map<String, Boolean>>(){}.getType();
                paymentMap = gson.fromJson(Files.readString(path), type);
            } else {
                paymentMap = new HashMap<>();
            }

            JsonObject jsonBody = gson.fromJson(req.body(), JsonObject.class);
            String orderId = jsonBody.get("orderId").getAsString();
            String status = jsonBody.get("status").getAsString();

            if("PAID".equals(status)) {
                paymentMap.put(orderId, true);
            }
            Files.writeString(path, gson.toJson(paymentMap));
            res.type("application/json");
            return gson.toJson(Map.of("error", 0, "message", "ok"));
        });
    }
}
