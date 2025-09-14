import com.google.gson.Gson;
import com.google.gson.JsonObject;
import model.Book;
import service.BookService;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;

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
            boolean isPaid = "true".equals(req.queryParams("paid"));
            if (!isPaid) {
                res.status(403);
                return "Bạn chưa thanh toán, không thể tải PDF";
            }

            res.type("application/pdf");
            res.header("Content-Disposition", "attachment; filename=ebook.pdf");
            res.raw().getOutputStream().write(bookService.getPdfBytes());
            return res.raw();
        });

        // Webhook PayOS
        post("/webhook", (req, res) -> {
            res.type("application/json");
            Gson gson = new Gson();
            try {
                // Parse JSON từ body
                JsonObject jsonBody = gson.fromJson(req.body(), JsonObject.class);
                String webhookUrl = jsonBody.get("webhookUrl").getAsString();

                // Gọi PayOS confirmWebhook
                var result = payOS.confirmWebhook(webhookUrl);

                // Trả về JSON giống Spring
                JsonObject response = new JsonObject();
                response.add("data", gson.toJsonTree(result));
                response.addProperty("error", 0);
                response.addProperty("message", "ok");

                return gson.toJson(response);
            } catch (Exception e) {
                JsonObject response = new JsonObject();
                response.add("data", null);
                response.addProperty("error", -1);
                response.addProperty("message", e.getMessage());
                e.printStackTrace();
                return gson.toJson(response);
            }
        });
    }
}
