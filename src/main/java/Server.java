import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;
import io.github.cdimascio.dotenv.Dotenv;

import static spark.Spark.*;

public class Server {
    static String DATA_FILE = "public/data/ebook-data.json";
    static Gson gson = new Gson();
    public static void main(String[] args) {
        port(3030);
        staticFiles.externalLocation(
                Paths.get("public").toAbsolutePath().toString());
        Dotenv dotenv = Dotenv.load();
        String clientId = dotenv.get("PAYOS_CLIENT_ID");
        String apiKey = dotenv.get("PAYOS_API_KEY");
        String checksumKey = dotenv.get("PAYOS_CHECKSUM_KEY");

        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "http://localhost:3000"); // chỉ cho FE
            response.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type,Authorization");
        });
        final PayOS payOS = new PayOS(clientId, apiKey, checksumKey);

        post("/create-payment-link", (req, res) -> {
            String domain = "http://localhost:3000";
            Long orderCode = System.currentTimeMillis() / 1000;
            int quantity = Integer.parseInt(req.queryParams("quantity"));
            int price = Integer.parseInt(req.queryParams("price"));

            ItemData itemData = ItemData
                    .builder()
                    .name(req.queryParams("name"))
                    .quantity(quantity)
                    .price(price)
                    .build();

            PaymentData paymentData = PaymentData
                    .builder()
                    .orderCode(orderCode)
                    .amount(quantity * price)
                    .description("Thanh toán đơn hàng")
                    .returnUrl(domain + "/success.html")
                    .cancelUrl(domain + "/cancel.html")
                    .item(itemData)
                    .build();

            CheckoutResponseData result = payOS.createPaymentLink(paymentData);
            res.redirect(result.getCheckoutUrl(), 303);
            return "";
        });

        get("/admin", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String json = new String(Files.readAllBytes(Paths.get(DATA_FILE)));
            model.put("book", gson.fromJson(json, Map.class));
            return new ModelAndView(model, "admin-page.mustache");
        }, new MustacheTemplateEngine());

        post("/admin/update", (req, res) ->{
            String name =  req.queryParams("name");
            String description =  req.queryParams("description");
            double price = Double.parseDouble(req.queryParams("price"));
            String image = req.queryParams("image");
            Book book = new Book(name, description, price, image);
            try (FileWriter writer = new FileWriter(DATA_FILE)) {
                gson.toJson(book, writer);
            }
            res.type("text/html");
            return "<h1>Update Success</h1><a href='/admin'>Go back</a>";
        });

        get("/book-data", (req, res) -> {
            res.type("application/json");
            String json = new String(Files.readAllBytes(Paths.get(DATA_FILE)));
            return json;
        });

        get("/download", (req, res) -> {
            String orderCode = req.queryParams("orderCode");
            System.out.println("Người dùng tải file với orderCode: " + orderCode);

            res.type("application/pdf");
            res.header("Content-Disposition", "attachment; filename=ebook.pdf");

            byte[] fileBytes = Files.readAllBytes(Paths.get("public/data/ebook.pdf"));
            res.raw().getOutputStream().write(fileBytes);
            res.raw().getOutputStream().flush();
            res.raw().getOutputStream().close();

            return res.raw();
        });

        post("/webhook", (req, res) -> {
            res.type("application/json");
            Map<String, String> requestBody = gson.fromJson(req.body(), Map.class);

            Map<String, Object> response = new HashMap<>();
            try {
                String webhookUrl = requestBody.get("webhookUrl");
                String result = payOS.confirmWebhook(webhookUrl);

                response.put("data", result);
                response.put("error", 0);
                response.put("message", "ok");
            } catch (Exception e) {
                e.printStackTrace();
                response.put("error", -1);
                response.put("message", e.getMessage());
                response.put("data", null);
            }
            return gson.toJson(response);
        });

    }
}