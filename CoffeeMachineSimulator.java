import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CoffeeMachineSimulator {

    private static final String STATE_FILE = "machine_state.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private enum UserRole {
        CUSTOMER,
        ADMIN
    }
    
    public static class SaleLog {
        private final String drinkName;
        private final double price;
        private final double cost;
        private final double profit;
        private final String timestamp;

        public SaleLog(String drinkName, double price, double cost, double profit) {
            this.drinkName = drinkName;
            this.price = price;
            this.cost = cost;
            this.profit = profit;
            this.timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        }
        
        public SaleLog(String drinkName, double price, double cost, double profit, String timestamp) {
            this.drinkName = drinkName;
            this.price = price;
            this.cost = cost;
            this.profit = profit;
            this.timestamp = timestamp;
        }

        public String toJson() {
            return String.format(
                "{\"name\":\"%s\",\"price\":%.2f,\"cost\":%.2f,\"profit\":%.2f,\"time\":\"%s\"}",
                drinkName, price, cost, profit, timestamp
            ).replace(',', '.');
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s (Цена: %.2f лв., Печалба: %.2f лв.)", timestamp, drinkName, price, profit);
        }

        // getters for external use
        public String getDrinkName() { return drinkName; }
        public double getPrice() { return price; }
        public double getCost() { return cost; }
        public double getProfit() { return profit; }
        public String getTimestamp() { return timestamp; }
    }

    public static class Drink {
        private final String name;
        private final double price;
        private final Map<String, Integer> ingredients;

        public Drink(String name, double price, Map<String, Integer> ingredients) {
            this.name = name;
            this.price = price;
            this.ingredients = ingredients;
        }

        public String getName() {
            return name;
        }

        public double getPrice() {
            return price;
        }

        public Map<String, Integer> getIngredients() {
            return ingredients;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"name\":\"").append(name).append("\",");
            sb.append("\"price\":").append(String.format("%.2f", price).replace(',', '.')).append(",");
            
            sb.append("\"ingredients\":{");
            boolean firstIng = true;
            for (Map.Entry<String, Integer> entry : ingredients.entrySet()) {
                if (!firstIng) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                firstIng = false;
            }
            sb.append("}");
            sb.append("}");
            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("%s (Цена: %.2f лв.)", name, price);
        }
    }

    public static class CoffeeMachine {
        private final Map<String, Drink> menu;
        private final Map<String, Integer> inventory;
        private final Map<String, Double> ingredientCosts;
        private final List<SaleLog> salesHistory;
        private final Map<String, String> drinkImages; // map drink name -> image path
        private double cash;
        private double totalProfit;

        public CoffeeMachine() {
            this.menu = new HashMap<>();
            this.inventory = new HashMap<>();
            this.ingredientCosts = new HashMap<>();
            this.salesHistory = new ArrayList<>();
            this.drinkImages = new HashMap<>();
            this.cash = 0.0;
            this.totalProfit = 0.0;
            
            if (!loadState()) {
                initializeDefaultState();
            }
        }
        
        private void initializeDefaultState() {
             System.out.println("ℹ️ JSON файлът за състояние не е намерен или е повреден. Инициализация с фабрични настройки.");
            
            inventory.put("Вода (мл)", 5000);
            inventory.put("Мляко (мл)", 2000);
            inventory.put("Кафе на зърна (гр)", 1000);
            inventory.put("Захар (гр)", 500);
            inventory.put("Чай (пакетче)", 50); 
            inventory.put("Какао (гр)", 300); 
            
            ingredientCosts.put("Вода (мл)", 0.0001);
            ingredientCosts.put("Мляко (мл)", 0.003);
            ingredientCosts.put("Кафе на зърна (гр)", 0.05);
            ingredientCosts.put("Захар (гр)", 0.002);
            ingredientCosts.put("Чай (пакетче)", 0.15);
            ingredientCosts.put("Какао (гр)", 0.03); 

            this.cash = 0.0;
            this.totalProfit = 0.0;
            this.drinkImages.clear();

            // 1. Еспресо
            Map<String, Integer> espressoIngredients = new HashMap<>();
            espressoIngredients.put("Вода (мл)", 50);
            espressoIngredients.put("Кафе на зърна (гр)", 10);
            menu.put("Еспресо", new Drink("Еспресо", 1.80, espressoIngredients));

            // 2. Лате
            Map<String, Integer> latteIngredients = new HashMap<>();
            latteIngredients.put("Вода (мл)", 30);
            latteIngredients.put("Кафе на зърна (гр)", 10);
            latteIngredients.put("Мляко (мл)", 150);
            menu.put("Лате", new Drink("Лате", 3.50, latteIngredients));
            
            // 3. Капучино
            Map<String, Integer> cappuccinoIngredients = new HashMap<>();
            cappuccinoIngredients.put("Вода (мл)", 50);
            cappuccinoIngredients.put("Кафе на зърна (гр)", 12); 
            cappuccinoIngredients.put("Мляко (мл)", 100); 
            menu.put("Капучино", new Drink("Капучино", 3.20, cappuccinoIngredients));
            
            // 4. Дълго Кафе (Американо)
            Map<String, Integer> americanoIngredients = new HashMap<>();
            americanoIngredients.put("Вода (мл)", 200);
            americanoIngredients.put("Кафе на зърна (гр)", 18);
            menu.put("Американo", new Drink("Американo", 2.50, americanoIngredients));
            // Note: original name "Американо" in Bulgarian - I've kept a similar one; adjust if needed.

            // 5. Горещ Шоколад
            Map<String, Integer> hotChocolateIngredients = new HashMap<>();
            hotChocolateIngredients.put("Мляко (мл)", 250);
            hotChocolateIngredients.put("Какао (гр)", 30);
            hotChocolateIngredients.put("Захар (гр)", 10);
            menu.put("Горещ Шоколад", new Drink("Горещ Шоколад", 4.00, hotChocolateIngredients));

            // 6. Фрапе
            Map<String, Integer> frappeIngredients = new HashMap<>();
            frappeIngredients.put("Вода (мл)", 50);
            frappeIngredients.put("Кафе на зърна (гр)", 15);
            frappeIngredients.put("Мляко (мл)", 50);
            frappeIngredients.put("Захар (гр)", 5);
            menu.put("Фрапе", new Drink("Фрапе", 3.80, frappeIngredients));
            
            // 7. Чай с Лимон
            Map<String, Integer> lemonTeaIngredients = new HashMap<>();
            lemonTeaIngredients.put("Вода (мл)", 300);
            lemonTeaIngredients.put("Чай (пакетче)", 1);
            lemonTeaIngredients.put("Захар (гр)", 5);
            menu.put("Чай с Лимон", new Drink("Чай с Лимон", 1.50, lemonTeaIngredients));
            
            // 8. Двойно Еспресо
            Map<String, Integer> doubleEspressoIngredients = new HashMap<>();
            doubleEspressoIngredients.put("Вода (мл)", 80);
            doubleEspressoIngredients.put("Кафе на зърна (гр)", 20);
            menu.put("Двойно Еспресо", new Drink("Двойно Еспресо", 2.80, doubleEspressoIngredients));
        }

        private String extractObjectContent(String json, String key) {
            String searchKey = "\"" + key + "\":";
            int keyStart = json.indexOf(searchKey);
            if (keyStart == -1) return null;
            int valueStart = keyStart + searchKey.length();
            
            if (json.charAt(valueStart) != '{') return null;

            int balance = 1;
            int valueEnd = valueStart + 1;
            while (valueEnd < json.length() && balance > 0) {
                char c = json.charAt(valueEnd);
                if (c == '{') balance++;
                else if (c == '}') balance--;
                valueEnd++;
            }
            if (balance == 0) {
                return json.substring(valueStart + 1, valueEnd - 1).trim();
            }
            return null;
        }

        private String extractArrayContent(String json, String key) {
            String searchKey = "\"" + key + "\":";
            int keyStart = json.indexOf(searchKey);
            if (keyStart == -1) return null;
            int valueStart = keyStart + searchKey.length();

            if (json.charAt(valueStart) != '[') return null;

            int balance = 1;
            int valueEnd = valueStart + 1;
            while (valueEnd < json.length() && balance > 0) {
                char c = json.charAt(valueEnd);
                if (c == '[') balance++;
                else if (c == ']') balance--;
                valueEnd++;
            }
            if (balance == 0) {
                return json.substring(valueStart + 1, valueEnd - 1).trim();
            }
            return null;
        }

        private String extractString(String json, String key) {
            String searchKey = "\"" + key + "\":\"";
            int start = json.indexOf(searchKey);
            if (start == -1) return null;
            int valueStart = start + searchKey.length();
            int valueEnd = json.indexOf("\"", valueStart);
            if (valueEnd == -1) return null;
            return json.substring(valueStart, valueEnd);
        }

        private double extractDouble(String json, String key) {
            String searchKey = "\"" + key + "\":";
            int start = json.indexOf(searchKey);
            if (start == -1) return 0.0;
            int valueStart = start + searchKey.length();
            
            int valueEnd = valueStart;
            while (valueEnd < json.length() && 
                   (Character.isDigit(json.charAt(valueEnd)) || 
                    json.charAt(valueEnd) == '.'||
                    json.charAt(valueEnd) == '-')) { 
                 valueEnd++;
            }
            while (valueEnd < json.length() && !Character.isWhitespace(json.charAt(valueEnd)) && 
                   json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') {
                valueEnd++;
            }
            
            try {
                 return Double.parseDouble(json.substring(valueStart, valueEnd).trim());
            } catch (NumberFormatException e) {
                 return 0.0;
            }
        }
        
        private <T> void parseMap(String mapContent, Map<String, T> map, Class<T> valueType) throws NumberFormatException {
            mapContent = mapContent.trim();
            if (mapContent.isEmpty()) return;

            String[] pairs = mapContent.split(",");
            for (String pair : pairs) {
                pair = pair.trim();
                int colonIndex = pair.indexOf(":");
                if (colonIndex > 0) {
                    String key = pair.substring(0, colonIndex).replace("\"", "").trim();
                    String valueStr = pair.substring(colonIndex + 1).trim();

                    if (!key.isEmpty()) {
                        if (valueType == Integer.class) {
                            Integer value = Integer.parseInt(valueStr);
                            map.put(key, (T) value);
                        } else if (valueType == Double.class) {
                            Double value = Double.parseDouble(valueStr); 
                            map.put(key, (T) value);
                        }
                    }
                }
            }
        }

        /**
         * Parse a JSON-like object content where values are strings:
         * Example content: "\"Еспресо\":\"C:\\\\images\\\\espresso.png\",\"Лате\":\"/home/user/latte.jpg\""
         */
        private void parseStringMap(String content, Map<String, String> map) {
            if (content == null) return;
            int i = 0;
            int n = content.length();
            while (i < n) {
                // skip whitespace and commas
                while (i < n && (Character.isWhitespace(content.charAt(i)) || content.charAt(i) == ',')) i++;
                if (i >= n) break;
                if (content.charAt(i) != '"') break;
                i++; // skip opening quote
                StringBuilder key = new StringBuilder();
                while (i < n) {
                    char c = content.charAt(i++);
                    if (c == '\\' && i < n) { key.append(content.charAt(i++)); continue; }
                    if (c == '"') break;
                    key.append(c);
                }
                // skip spaces then colon
                while (i < n && Character.isWhitespace(content.charAt(i))) i++;
                if (i < n && content.charAt(i) == ':') i++;
                while (i < n && Character.isWhitespace(content.charAt(i))) i++;
                if (i >= n || content.charAt(i) != '"') break;
                i++; // skip opening quote for value
                StringBuilder value = new StringBuilder();
                while (i < n) {
                    char c = content.charAt(i++);
                    if (c == '\\' && i < n) { value.append(content.charAt(i++)); continue; }
                    if (c == '"') break;
                    value.append(c);
                }
                map.put(key.toString(), value.toString());
                // i now at char after closing quote; loop continues
            }
        }

        private String escapeJsonString(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        public void saveState() {
            try (PrintWriter writer = new PrintWriter(new FileWriter(STATE_FILE))) {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                
                sb.append("\"cash\":").append(String.format("%.2f", this.cash).replace(',', '.')).append(",");
                sb.append("\"totalProfit\":").append(String.format("%.2f", this.totalProfit).replace(',', '.')).append(",");
                
                sb.append("\"ingredientCosts\":{");
                boolean firstCost = true;
                for (Map.Entry<String, Double> entry : ingredientCosts.entrySet()) {
                    if (!firstCost) sb.append(",");
                    sb.append("\"").append(entry.getKey()).append("\":").append(String.format("%.4f", entry.getValue()).replace(',', '.'));
                    firstCost = false;
                }
                sb.append("},");
                
                sb.append("\"inventory\":{");
                boolean firstInv = true;
                for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
                    if (!firstInv) sb.append(",");
                    sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                    firstInv = false;
                }
                sb.append("},");

                // save drinkImages map (string values)
                sb.append("\"drinkImages\":{");
                boolean firstImg = true;
                for (Map.Entry<String, String> entry : drinkImages.entrySet()) {
                    if (!firstImg) sb.append(",");
                    sb.append("\"").append(escapeJsonString(entry.getKey())).append("\":");
                    sb.append("\"").append(escapeJsonString(entry.getValue())).append("\"");
                    firstImg = false;
                }
                sb.append("},");

                sb.append("\"menu\":[");
                boolean firstMenu = true;
                for (Drink drink : menu.values()) {
                    if (!firstMenu) sb.append(",");
                    sb.append(drink.toJson());
                    firstMenu = false;
                }
                sb.append("],");
                
                sb.append("\"salesHistory\":[");
                boolean firstSale = true;
                for (SaleLog log : salesHistory) {
                    if (!firstSale) sb.append(",");
                    sb.append(log.toJson());
                    firstSale = false;
                }
                sb.append("]");
                
                sb.append("}");
                writer.print(sb.toString());

                System.out.println("✅ Състоянието е успешно запазено във JSON файла: " + STATE_FILE);
            } catch (IOException e) {
                System.out.println("❌ Грешка при записване на състоянието: " + e.getMessage());
            }
        }

        public boolean loadState() {
            File file = new File(STATE_FILE);
            if (!file.exists()) {
                return false; 
            }
            
            try (Scanner fileScanner = new Scanner(file).useDelimiter("\\A")) {
                if (!fileScanner.hasNext()) return false;
                String jsonContent = fileScanner.next().trim();

                this.menu.clear();
                this.inventory.clear();
                this.ingredientCosts.clear();
                this.salesHistory.clear();
                this.drinkImages.clear();

                this.cash = extractDouble(jsonContent, "cash");
                this.totalProfit = extractDouble(jsonContent, "totalProfit");

                String costsString = extractObjectContent(jsonContent, "ingredientCosts");
                if (costsString != null) {
                    parseMap(costsString, (Map)ingredientCosts, Double.class);
                }

                String inventoryString = extractObjectContent(jsonContent, "inventory");
                if (inventoryString != null) {
                    parseMap(inventoryString, (Map)inventory, Integer.class);
                }

                String imagesString = extractObjectContent(jsonContent, "drinkImages");
                if (imagesString != null) {
                    parseStringMap(imagesString, (Map)drinkImages);
                }

                String menuArrayString = extractArrayContent(jsonContent, "menu");
                if (menuArrayString != null) {
                    String[] drinkObjects = menuArrayString.split("(?<=}),(?=\\{)"); 
                    for (String drinkJson : drinkObjects) {
                        if (drinkJson.trim().isEmpty()) continue;
                        
                        String name = extractString(drinkJson, "name");
                        double price = extractDouble(drinkJson, "price");
                        
                        String ingredientsString = extractObjectContent(drinkJson, "ingredients");
                        Map<String, Integer> ingredients = new HashMap<>();
                        if (ingredientsString != null) {
                            parseMap(ingredientsString, (Map)ingredients, Integer.class);
                        }

                        if (name != null && price >= 0) {
                            Drink drink = new Drink(name, price, ingredients);
                            menu.put(name, drink);
                        }
                    }
                }
                
                String salesArrayString = extractArrayContent(jsonContent, "salesHistory");
                if (salesArrayString != null) {
                    String[] saleObjects = salesArrayString.split("(?<=}),(?=\\{)"); 
                    for (String saleJson : saleObjects) {
                        if (saleJson.trim().isEmpty()) continue;
                        
                        String name = extractString(saleJson, "name");
                        double price = extractDouble(saleJson, "price");
                        double cost = extractDouble(saleJson, "cost");
                        double profit = extractDouble(saleJson, "profit");
                        String time = extractString(saleJson, "time");
                        
                        if (name != null) {
                            salesHistory.add(new SaleLog(name, price, cost, profit, time));
                        }
                    }
                }
                
                System.out.println("✅ Състоянието е успешно заредено от JSON файла: " + STATE_FILE);
                return true;
            } catch (IOException | NumberFormatException | NullPointerException e) {
                System.out.println("❌ Грешка при зареждане/парсване на JSON състоянието: " + e.getMessage());
                initializeDefaultState(); 
                return false;
            }
        }

        private double calculateDrinkCost(Drink drink) {
            double cost = 0.0;
            for (Map.Entry<String, Integer> entry : drink.getIngredients().entrySet()) {
                String ingredientName = entry.getKey();
                int requiredAmount = entry.getValue();
                double unitCost = ingredientCosts.getOrDefault(ingredientName, 0.0);
                cost += requiredAmount * unitCost;
            }
            return cost;
        }

        private boolean hasEnoughIngredients(Drink drink) {
            for (Map.Entry<String, Integer> entry : drink.getIngredients().entrySet()) {
                String ingredientName = entry.getKey();
                int requiredAmount = entry.getValue();
                int currentAmount = inventory.getOrDefault(ingredientName, 0);

                if (currentAmount < requiredAmount) {
                    System.out.println("   (Недостатъчно " + ingredientName + " за " + drink.getName() + 
                                       ". Налични: " + currentAmount + ")");
                    return false;
                }
            }
            return true;
        }
        
        public boolean checkTotalIngredients(List<String> drinkNames) {
            Map<String, Integer> tempInventory = new HashMap<>(inventory);
            boolean allAvailable = true;
            
            for (String drinkName : drinkNames) {
                Drink drink = menu.get(drinkName);
                if (drink == null) continue;
                
                for (Map.Entry<String, Integer> entry : drink.getIngredients().entrySet()) {
                    String ingredientName = entry.getKey();
                    int requiredAmount = entry.getValue();
                    
                    int currentAmount = tempInventory.getOrDefault(ingredientName, 0);
                    
                    if (currentAmount < requiredAmount) {
                        System.out.println("❌ Грешка в запасите: Недостатъчно " + ingredientName + " за " + drinkName + ".");
                        allAvailable = false;
                    } else {
                        tempInventory.put(ingredientName, currentAmount - requiredAmount);
                    }
                }
            }
            
            if (!allAvailable) {
                System.out.println("🚫 Поръчката е отказана поради липса на съставки.");
            }
            return allAvailable;
        }

        public void makeSingleDrink(String drinkName) {
            Drink drink = menu.get(drinkName);

            if (!hasEnoughIngredients(drink)) {
                System.out.println("❌ Грешка: Грешка в запасите при изпълнение на " + drinkName + ".");
                return;
            }

            double cost = calculateDrinkCost(drink);
            double profit = drink.getPrice() - cost;
            
            consumeIngredients(drink);
            
            cash += drink.getPrice();
            totalProfit += profit;
            
            salesHistory.add(new SaleLog(drinkName, drink.getPrice(), cost, profit));
            
            System.out.println("🎉 УСПЕХ! Приготвено: " + drinkName);
            
            saveState();
        }

        // CSV logging moved to UI layer to allow transaction-level writes

        private void consumeIngredients(Drink drink) {
            for (Map.Entry<String, Integer> entry : drink.getIngredients().entrySet()) {
                String ingredientName = entry.getKey();
                int consumedAmount = entry.getValue();
                inventory.computeIfPresent(ingredientName, (key, current) -> current - consumedAmount);
            }
        }
        
        public void addDrink(String name, double price, Map<String, Integer> ingredients) {
            if (menu.containsKey(name)) {
                System.out.println("❌ Напитка '" + name + "' вече съществува в менюто. Използвайте команда за редактиране.");
                return;
            }
            
            for (String ingredient : ingredients.keySet()) {
                if (!ingredientCosts.containsKey(ingredient)) {
                    System.out.println("❌ Грешка: Съставката '" + ingredient + "' е непозната. Добавете я с цена преди да я използвате.");
                    return;
                }
                if (!inventory.containsKey(ingredient)) {
                    inventory.put(ingredient, 0);
                }
            }

            Drink newDrink = new Drink(name, price, ingredients);
            menu.put(name, newDrink);
            System.out.println("✅ Успешно добавена нова напитка: " + newDrink);
            saveState();
        }

        public void deleteDrink(String name) {
            if (!menu.containsKey(name)) {
                System.out.println("❌ Напитка '" + name + "' не е намерена в менюто.");
                return;
            }
            menu.remove(name);
            // remove associated image if any
            drinkImages.remove(name);
            System.out.println("✅ Успешно изтрита напитка: " + name);
            saveState();
        }
        
        public void displayProfitAndReport() {
            System.out.println("\n--- ФИНАНСОВ ОТЧЕТ И СТАТИСТИКА ---");
            System.out.println(String.format("💰 Събрани пари в касата (БРУТО): %.2f лв.", cash));
            System.out.println(String.format("📈 Обща реализирана ПЕЧАЛБА (НЕТО): %.2f лв.", totalProfit));
            System.out.println("------------------------------------");
            
            if (salesHistory.isEmpty()) {
                System.out.println("Няма регистрирани продажби.");
                return;
            }

            System.out.println(String.format("📊 Общ брой продадени напитки: %d", salesHistory.size()));
            
            Map<String, Long> drinkCounts = salesHistory.stream()
                .collect(Collectors.groupingBy(log -> log.drinkName, Collectors.counting()));
                
            System.out.println("Топ 3 най-продавани напитки:");
            drinkCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> System.out.println(String.format("  - %s: %d продажби", entry.getKey(), entry.getValue())));

            System.out.println("\nПоследни 5 продажби:");
            int count = 0;
            for (int i = salesHistory.size() - 1; i >= 0 && count < 5; i--, count++) {
                System.out.println("  " + salesHistory.get(i));
            }
            System.out.println("------------------------------------");
        }
        
        public double collectCash() {
            double collected = this.cash;
            this.cash = 0.0;
            saveState(); 
            return collected;
        }

        public Map<String, Drink> getMenu() {
            return menu;
        }
        
        public Map<String, Integer> getInventory() {
            return inventory;
        }
        
        public List<SaleLog> getSalesHistory() {
            return new ArrayList<>(salesHistory);
        }

        public double getCashAmount() {
            return cash;
        }

        public double getTotalProfitAmount() {
            return totalProfit;
        }

        public Map<String, Double> getIngredientCosts() {
            return new HashMap<>(ingredientCosts);
        }

        // image API
        public void setDrinkImage(String drinkName, String path) {
            if (!menu.containsKey(drinkName)) {
                System.out.println("❌ Не може да се добави изображение: напитка '" + drinkName + "' не съществува.");
                return;
            }
            drinkImages.put(drinkName, path);
            System.out.println("✅ Изображение прикачено към: " + drinkName);
            saveState();
        }

        public String getDrinkImage(String drinkName) {
            return drinkImages.get(drinkName);
        }

        public Map<String, String> getAllDrinkImages() {
            return new HashMap<>(drinkImages);
        }

        public void displayMenu() {
            System.out.println("\n--- МЕНЮ ---");
            if (menu.isEmpty()) {
                System.out.println("Менюто е празно.");
                return;
            }
            menu.forEach((name, drink) -> {
                double cost = calculateDrinkCost(drink);
                System.out.println(String.format("%s - %.2f лв. (Себестойност: %.2f лв.)", name, drink.getPrice(), cost));
            });
            System.out.println("------------");
        }
        
        public void displayInventory() {
            System.out.println("\n--- ТЕКУЩИ ЗАПАСИ ---");
            inventory.forEach((ingredient, amount) -> {
                double costPerUnit = ingredientCosts.getOrDefault(ingredient, 0.0);
                System.out.println(String.format("%s: %d (Цена/Единица: %.4f лв.)", ingredient, amount, costPerUnit));
            });
            System.out.println(String.format("Събрани пари в касата: %.2f лв.", cash));
            System.out.println(String.format("Обща печалба: %.2f лв.", totalProfit));
            System.out.println("----------------------");
        }
        
        public void refillInventory(String ingredient, int amount) {
            if (amount <= 0) {
                 System.out.println("❌ Грешка при зареждане: Количеството трябва да е положително.");
                 return;
            }
            
            if (!ingredientCosts.containsKey(ingredient)) {
                System.out.println("❌ Грешка при зареждане: Непозната съставка '" + ingredient + "'. Моля, добавете я към системата с цена.");
                return;
            }

            inventory.compute(ingredient, (key, current) -> (current == null ? 0 : current) + amount);
            System.out.println(String.format("✅ Успешно заредени %d на %s.", amount, ingredient));
            
            saveState(); 
        }
    }

    public static void main(String[] args) {
        CoffeeMachine machine = new CoffeeMachine();
        Scanner scanner = new Scanner(System.in);
        boolean isRunning = true;
        UserRole currentRole = UserRole.CUSTOMER;

        System.out.println("☕️ ДОБРЕ ДОШЛИ В СИМУЛАТОРА НА КАФЕМАШИНА!");
        machine.displayInventory();

        while (isRunning) {
            displayPrompt(currentRole);
            
            String command = scanner.nextLine().trim().toLowerCase();

            try {
                 switch (command) {
                    case "меню":
                        machine.displayMenu();
                        break;
                    case "купи":
                        handleBuy(scanner, machine);
                        break;
                    case "запаси":
                        machine.displayInventory();
                        break;
                    case "админ":
                        currentRole = handleAdminLogin(scanner, currentRole);
                        break;
                    case "зареди":
                        if (currentRole == UserRole.ADMIN) {
                            handleRefill(scanner, machine);
                        } else {
                            System.out.println("🚫 Отказан достъп. Тази команда е само за Администратори.");
                        }
                        break;
                    case "каса":
                        if (currentRole == UserRole.ADMIN) {
                            handleCollectCash(machine);
                        } else {
                            System.out.println("🚫 Отказан достъп. Тази команда е само за Администратори.");
                        }
                        break;
                    case "добави":
                        if (currentRole == UserRole.ADMIN) {
                            handleAddDrink(scanner, machine);
                        } else {
                            System.out.println("🚫 Отказан достъп. Тази команда е само за Администратори.");
                        }
                        break;
                    case "изтрий":
                        if (currentRole == UserRole.ADMIN) {
                            handleDeleteDrink(scanner, machine);
                        } else {
                            System.out.println("🚫 Отказан достъп. Тази команда е само за Администратори.");
                        }
                        break;
                    case "отчет":
                        if (currentRole == UserRole.ADMIN) {
                            machine.displayProfitAndReport();
                        } else {
                            System.out.println("🚫 Отказан достъп. Тази команда е само за Администратори.");
                        }
                        break;
                    case "изход":
                        isRunning = false;
                        System.out.println("Изключване на кафемашината. Довиждане!");
                        break;
                    default:
                        System.out.println("❓ Невалидна команда. Въведете 'меню', 'купи', 'запаси', 'админ' или 'изход'.");
                }
            } catch (InputMismatchException e) {
                System.out.println("⚠️ Грешка при въвеждане. Моля, въведете коректен тип данни.");
                if (scanner.hasNextLine()) scanner.nextLine();
            } catch (Exception e) {
                System.out.println("⚠️ Възникна неочаквана грешка: " + e.getMessage());
            }
        }
        
        scanner.close();
    }
    
    private static void displayPrompt(UserRole role) {
        String prompt = "\n--- КОМАНДИ: меню | купи | запаси | админ | изход ";
        if (role == UserRole.ADMIN) {
            prompt += "| зареди | каса | добави | изтрий | отчет ";
            System.out.println(prompt + "--- (РОЛЯ: АДМИН)");
        } else {
            System.out.println(prompt + "---");
        }
        System.out.print("Въведете команда: ");
    }
    
    private static UserRole handleAdminLogin(Scanner scanner, UserRole currentRole) {
        if (currentRole == UserRole.ADMIN) {
            System.out.println("➡️ Излизане от режим Администратор.");
            return UserRole.CUSTOMER;
        }

        final String ADMIN_PASS = "1234"; 
        
        System.out.print("Въведете парола за Администратор: ");
        String password = scanner.nextLine().trim();

        if (password.equals(ADMIN_PASS)) {
            System.out.println("🎉 УСПЕХ! Влязохте като Администратор.");
            return UserRole.ADMIN;
        } else {
            System.out.println("❌ Грешна парола.");
            return UserRole.CUSTOMER;
        }
    }
    
    private static void handleAddDrink(Scanner scanner, CoffeeMachine machine) {
        System.out.println("\n--- ДОБАВЯНЕ НА НОВА НАПИТКА ---");
        System.out.print("Въведете име на напитката: ");
        String name = scanner.nextLine().trim();
        
        System.out.print("Въведете продажна цена (напр. 3.50): ");
        double price = readDoubleInput(scanner);
        if (price == -1.0) return;

        Map<String, Integer> ingredients = new HashMap<>();
        System.out.println("Въведете съставките (край с 'стоп'):");
        
        while (true) {
            System.out.print("Съставка (име или 'стоп'): ");
            String ingredientName = scanner.nextLine().trim();
            if (ingredientName.equalsIgnoreCase("стоп")) break;
            
            System.out.print("Количество в мерна единица (напр. 150): ");
            int amount = readIntInput(scanner);
            if (amount == -1) return;
            
            ingredients.put(ingredientName, amount);
        }
        
        if (ingredients.isEmpty()) {
            System.out.println("❌ Грешка: Напитката трябва да има поне една съставка.");
            return;
        }

        machine.addDrink(name, price, ingredients);
    }
    
    private static void handleDeleteDrink(Scanner scanner, CoffeeMachine machine) {
        machine.displayMenu();
        System.out.print("Въведете името на напитката за ИЗТРИВАНЕ: ");
        String name = scanner.nextLine().trim();
        machine.deleteDrink(name);
    }

    private static void handleBuy(Scanner scanner, CoffeeMachine machine) {
        machine.displayMenu();
        
        System.out.print("Въведете напитките за поръчка, разделени със запетая (напр. Еспресо, Лате): ");
        String orderInput = scanner.nextLine().trim();
        
        String[] drinkNameArray = orderInput.split(",");
        List<String> orderedDrinkNames = new ArrayList<>();
        double totalCost = 0.0;
        
        for (String name : drinkNameArray) {
            String cleanName = name.trim();
            Drink drink = machine.getMenu().get(cleanName);
            
            if (drink == null) {
                System.out.println("❌ Грешка: Напитка '" + cleanName + "' не е в менюто. Поръчката е отказана.");
                return;
            }
            
            orderedDrinkNames.add(cleanName);
            totalCost += drink.getPrice();
        }
        
        System.out.println(String.format("Обща цена на поръчката (%d напитки): %.2f лв.", orderedDrinkNames.size(), totalCost));
        
        if (!machine.checkTotalIngredients(orderedDrinkNames)) {
            return;
        }

        System.out.print("Въведете общата сума пари (напр. 10.00): ");
        double totalMoney = readDoubleInput(scanner);
        if (totalMoney == -1.0) return;

        if (totalMoney < totalCost) {
            System.out.println(String.format("❌ Грешка: Недостатъчно пари. Нужни са %.2f лв. Върната сума: %.2f лв.", totalCost, totalMoney));
            return;
        }
        
        System.out.println("\n--- ИЗПЪЛНЕНИЕ НА ПОРЪЧКАТА ---");

        for (String drinkName : orderedDrinkNames) {
             System.out.println(String.format("... Приготвяне на %s...", drinkName));
             machine.makeSingleDrink(drinkName); 
        }
        
        double finalChange = totalMoney - totalCost;
        
        System.out.println("\n--- РЕЗУЛТАТ ОТ ПОРЪЧКАТА ---");
        System.out.println(String.format("Платена сума: %.2f лв.", totalMoney));
        System.out.println(String.format("Обща цена на поръчката: %.2f лв.", totalCost));
        System.out.println(String.format("💰 Вашето общо ресто е: %.2f лв.", finalChange));
        System.out.println("--------------------------------");
    }

    private static void handleCollectCash(CoffeeMachine machine) {
        double collected = machine.collectCash();
        System.out.println(String.format("💼 Успешно изтеглени %.2f лв. от касата. Касата е нулирана.", collected));
    }
    
    private static void handleRefill(Scanner scanner, CoffeeMachine machine) {
        Map<String, Integer> inventory = machine.getInventory();
        
        if (inventory.isEmpty()) {
            System.out.println("❌ Инвентарът е празен.");
            return;
        }

        System.out.println("\n--- ИЗБЕРЕТЕ СЪСТАВКА ЗА ЗАРЕЖДАНЕ ---");
        List<String> ingredientNames = new ArrayList<>(inventory.keySet());
        for (int i = 0; i < ingredientNames.size(); i++) {
            String name = ingredientNames.get(i);
            System.out.println(String.format("  [%d] %s (Текущо: %d)", i + 1, name, inventory.get(name)));
        }
        System.out.println("----------------------------------------");
        System.out.print("Въведете номер на съставката (или 0 за отказ): ");

        int choice = readIntInput(scanner, true); 
        if (choice == -1 || choice == 0) {
            System.out.println("Зареждането е отказано.");
            return;
        }
        
        if (choice < 1 || choice > ingredientNames.size()) {
            System.out.println("❌ Невалиден номер. Моля, изберете номер от списъка.");
            return;
        }
        
        String ingredientName = ingredientNames.get(choice - 1);

        System.out.print("Въведете количеството за добавяне: ");
        int amount = readIntInput(scanner, false); 
        if (amount == -1) return;

        machine.refillInventory(ingredientName, amount);
    }
    
    private static double readDoubleInput(Scanner scanner) {
        try {
            String input = scanner.nextLine().replace(',', '.').trim();
            double value = Double.parseDouble(input);
            if (value < 0) {
                 System.out.println("❌ Сумата не може да бъде отрицателна.");
                 return -1.0;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("❌ Невалиден формат за сума. Моля, въведете число.");
            return -1.0;
        }
    }
    
    private static int readIntInput(Scanner scanner, boolean allowZeroOrNegative) {
        try {
            String input = scanner.nextLine().trim();
            int value = Integer.parseInt(input);
            
            if (!allowZeroOrNegative && value <= 0) {
                 System.out.println("❌ Количеството трябва да е положително.");
                 return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("❌ Невалиден формат за количество. Моля, въведете цяло число.");
            return -1;
        }
    }
    
     private static int readIntInput(Scanner scanner) {
        return readIntInput(scanner, false);
     }
}