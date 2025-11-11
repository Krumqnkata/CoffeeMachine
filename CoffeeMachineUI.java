import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.text.DecimalFormat;


public class CoffeeMachineUI {

    private CoffeeMachineSimulator.CoffeeMachine machine;
    private JFrame frame;
    private DefaultListModel<String> menuListModel;
    private JList<String> menuList;
    private JList<String> orderList;
    private JTextArea inventoryText;
    private JTextArea salesText;
    private JTextArea consoleText;
    private JLabel imageLabel;
    private boolean isAdmin = false;

    
    private JButton adminSetImageBtn;
    private JButton adminRemoveImageBtn;
    private JButton adminAddDrinkBtn;
    private JButton adminEditDrinkBtn; 
    private JButton adminDeleteDrinkBtn;
    private JButton adminRefillBtn;
    private JButton adminCollectBtn;
    private JButton adminReportBtn;
    private JButton adminSetBackgroundBtn;

    private JButton viewPriceBtn;

    
    private JTabbedPane rightTabs;
    private JTabbedPane leftTabs;
    private JSplitPane splitPane;
    private JButton adminLoginBtn;


    private BackgroundPanel backgroundPanel;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat PRICE_FMT = new DecimalFormat("0.00");

    // *** НОВО *** Праг за сигнализиране на нисък инвентар
    private static final int LOW_STOCK_THRESHOLD = 100;

    public CoffeeMachineUI() {
        // Console for internal logs
        consoleText = new JTextArea();
        consoleText.setEditable(false);
        consoleText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        redirectSystemStreamsToConsole(consoleText);

        // instantiate machine AFTER redirect so logs show up in console area
        machine = new CoffeeMachineSimulator.CoffeeMachine();

        frame = new JFrame("Coffee Machine Simulator - GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);

        // Background panel (content pane)
        backgroundPanel = new BackgroundPanel();
        backgroundPanel.setLayout(new BorderLayout());
        frame.setContentPane(backgroundPanel);

        // Top panel with admin login
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        topPanel.setOpaque(false); // show background through
        JLabel welcome = new JLabel("☕️ Coffee Machine Simulator (GUI)");
        topPanel.add(welcome, BorderLayout.WEST);

        adminLoginBtn = new JButton("Вход Админ");
        adminLoginBtn.addActionListener(e -> handleAdminToggle("123456789"));
        topPanel.add(adminLoginBtn, BorderLayout.EAST);

        backgroundPanel.add(topPanel, BorderLayout.NORTH);

        // Center split pane
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.55);
        splitPane.setOpaque(false);

        // Left tabs (Menu & Order)
        leftTabs = new JTabbedPane();
        leftTabs.setOpaque(false);

        // Menu tab
        menuListModel = new DefaultListModel<>();
        menuList = new JList<>(menuListModel);
        menuList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        menuList.setBackground(new Color(255, 255, 255, 220));
        menuList.setOpaque(true);

        JScrollPane menuScroll = new JScrollPane(menuList);
        menuScroll.setOpaque(false);
        menuScroll.getViewport().setOpaque(false);

        imageLabel = new JLabel(" ");
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        imageLabel.setPreferredSize(new Dimension(260, 200));
        JPanel imageContainer = new JPanel(new BorderLayout());
        imageContainer.setBorder(BorderFactory.createTitledBorder("Изображение на напитката"));
        imageContainer.setOpaque(false);
        imageContainer.add(imageLabel, BorderLayout.CENTER);

        JPanel menuButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        menuButtons.setOpaque(false);
        JButton refreshMenuBtn = new JButton("Обнови менюто");
        refreshMenuBtn.addActionListener(e -> refreshMenuList());
        menuButtons.add(refreshMenuBtn);

        viewPriceBtn = new JButton("Покажи себестойност");
        viewPriceBtn.addActionListener(e -> showSelectedCost());
        menuButtons.add(viewPriceBtn);

        JPanel menuPanel = new JPanel(new BorderLayout(8, 8));
        menuPanel.setOpaque(false);
        menuPanel.setBorder(new EmptyBorder(6, 6, 6, 6));
        menuPanel.add(menuScroll, BorderLayout.CENTER);
        menuPanel.add(imageContainer, BorderLayout.EAST);
        menuPanel.add(menuButtons, BorderLayout.SOUTH);
        leftTabs.addTab("Меню", menuPanel);

        // Order tab
        orderList = new JList<>(menuListModel); // Uses the *same* model
        orderList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        orderList.setBackground(new Color(255,255,255,220));
        orderList.setOpaque(true);
        JScrollPane orderScroll = new JScrollPane(orderList);
        orderScroll.setOpaque(false);
        orderScroll.getViewport().setOpaque(false);

        JPanel orderBottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        orderBottom.setOpaque(false);
        JButton orderBtn = new JButton("Бърза поръчка (всяка по 1) 💵/💳");
        orderBtn.addActionListener(e -> handleQuickOrder(orderList));
        orderBottom.add(orderBtn);
        JButton orderWithQtyBtn = new JButton("Поръчай с количество 📦");
        orderWithQtyBtn.addActionListener(e -> handleOrderWithQuantities(orderList));
        orderBottom.add(orderWithQtyBtn);
        JButton clearSelection = new JButton("Изчисти избора");
        clearSelection.addActionListener(ev -> orderList.clearSelection());
        orderBottom.add(clearSelection);

        JPanel orderPanel = new JPanel(new BorderLayout(8,8));
        orderPanel.setOpaque(false);
        orderPanel.add(new JLabel("Изберете напитки и натиснете 'Поръчай' или 'Поръчай с количество'."), BorderLayout.NORTH);
        orderPanel.add(orderScroll, BorderLayout.CENTER);
        orderPanel.add(orderBottom, BorderLayout.SOUTH);
        leftTabs.addTab("Поръчка", orderPanel);

        // When switching between Menu and Order tabs, update the displayed image
        leftTabs.addChangeListener(e -> updateDisplayedImageForSelectedMenuItem());

        splitPane.setLeftComponent(leftTabs);

        // Right tabs (admin-only)
        rightTabs = buildRightTabs();

        // Initially hide right panel for non-admin
        splitPane.setRightComponent(new JPanel());

        backgroundPanel.add(splitPane, BorderLayout.CENTER);

        // Status bar
        JLabel status = new JLabel("Готово.");
        status.setBorder(new EmptyBorder(6,6,6,6));
        status.setOpaque(false);
        backgroundPanel.add(status, BorderLayout.SOUTH);

        // selection listener to update image
        menuList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                orderList.setSelectedValue(menuList.getSelectedValue(), false);
                updateDisplayedImageForSelectedMenuItem();
            }
        });

        // also update image when selecting in order tab
        orderList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDisplayedImageForSelectedMenuItem();
            }
        });

        // initial data
        refreshAllUI();

        // auto load bg_coffee.jpg if present
        File autoBg = new File("bg_coffee.jpg");
        if (autoBg.exists()) {
            try {
                BufferedImage img = ImageIO.read(autoBg);
                if (img != null) backgroundPanel.setBackgroundImage(img, 0.25f);
            } catch (IOException ex) {
                System.err.println("bg_coffee.jpg load failed: " + ex.getMessage());
            }
        }

        // initial admin state
        updateAdminState();

        frame.setVisible(true);
    }

    /**
     * Refresh all main UI areas. Called manually after state-changing actions.
     */
    private void refreshAllUI() {
        try {
            int menuIdx = menuList.getSelectedIndex();
            
            refreshMenuList();
            refreshInventoryArea();
            refreshSalesArea();
            updateDisplayedImageForSelectedMenuItem();

            if (menuIdx >= 0 && menuIdx < menuListModel.getSize()) {
                menuList.setSelectedIndex(menuIdx);
            }
           
            if (backgroundPanel != null) {
                backgroundPanel.revalidate();
                backgroundPanel.repaint();
            }
            if (frame != null) {
                frame.revalidate();
                frame.repaint();
            }
        } catch (Exception ex) {
            System.err.println("Error during UI refresh: " + ex.getMessage());
        }
    }

    /**
     * Build right-side tabs (inventory, sales, console, admin).
     */
    private JTabbedPane buildRightTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(false);

        // Inventory
        inventoryText = new JTextArea();
        inventoryText.setEditable(false);
        inventoryText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        inventoryText.setBackground(new Color(255,255,255,220));
        inventoryText.setOpaque(true);
        JScrollPane invScroll = new JScrollPane(inventoryText);
        invScroll.setOpaque(false);
        invScroll.getViewport().setOpaque(false);

        JPanel invPanel = new JPanel(new BorderLayout());
        invPanel.setBorder(new EmptyBorder(6,6,6,6));
        invPanel.setOpaque(false);
        invPanel.add(invScroll, BorderLayout.CENTER);

        JPanel invButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        invButtons.setOpaque(false);
        JButton refreshInv = new JButton("Обнови запаси");
        refreshInv.addActionListener(e -> refreshInventoryArea());
        invButtons.add(refreshInv);

        adminRefillBtn = new JButton("Зареди");
        adminRefillBtn.addActionListener(e -> handleRefillDialog());
        invButtons.add(adminRefillBtn);

        adminCollectBtn = new JButton("Вземи каса");
        adminCollectBtn.addActionListener(e -> handleCollectCash());
        invButtons.add(adminCollectBtn);

        invPanel.add(invButtons, BorderLayout.SOUTH);
        tabs.addTab("Запаси", invPanel);

        // Sales
        salesText = new JTextArea();
        salesText.setEditable(false);
        salesText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        salesText.setBackground(new Color(255,255,255,220));
        salesText.setOpaque(true);
        JScrollPane salesScroll = new JScrollPane(salesText);
        salesScroll.setOpaque(false);
        salesScroll.getViewport().setOpaque(false);

        JPanel salesPanel = new JPanel(new BorderLayout());
        salesPanel.setBorder(new EmptyBorder(6,6,6,6));
        salesPanel.setOpaque(false);
        salesPanel.add(salesScroll, BorderLayout.CENTER);

        JPanel salesButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        salesButtons.setOpaque(false);
        JButton refreshSales = new JButton("Обнови отчет");
        refreshSales.addActionListener(e -> refreshSalesArea());
        salesButtons.add(refreshSales);

        adminReportBtn = new JButton("Покажи отчет (конзола)");
        adminReportBtn.addActionListener(e -> machine.displayProfitAndReport());
        salesButtons.add(adminReportBtn);

        JButton exportCsvBtn = new JButton("Експортирай CSV");
        exportCsvBtn.addActionListener(e -> {
            File csv = new File("sales_log.csv");
            if (!csv.exists()) { JOptionPane.showMessageDialog(frame, "Няма CSV файл за експорт.", "Експорт", JOptionPane.INFORMATION_MESSAGE); return; }
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Експортирай sales_log.csv като...");
            chooser.setSelectedFile(new File("sales_log_export.csv"));
            int res = chooser.showSaveDialog(frame);
            if (res != JFileChooser.APPROVE_OPTION) return;
            File target = chooser.getSelectedFile();
            try (FileInputStream in = new FileInputStream(csv); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192]; int r;
                while ((r = in.read(buf)) > 0) out.write(buf,0,r);
                JOptionPane.showMessageDialog(frame, "CSV експортиран: " + target.getAbsolutePath(), "Експорт", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Грешка при експортиране: " + ex.getMessage(), "Грешка", JOptionPane.ERROR_MESSAGE);
            }
        });
        salesButtons.add(exportCsvBtn);

        JButton clearCsvBtn = new JButton("Изтрий CSV");
        clearCsvBtn.addActionListener(e -> {
            File csv = new File("sales_log.csv");
            if (!csv.exists()) { JOptionPane.showMessageDialog(frame, "Няма CSV файл за изтриване.", "Изтриване", JOptionPane.INFORMATION_MESSAGE); return; }
            int ans = JOptionPane.showConfirmDialog(frame, "Сигурни ли сте, че искате да изтриете sales_log.csv?", "Потвърждение", JOptionPane.YES_NO_OPTION);
            if (ans != JOptionPane.YES_OPTION) return;
            try (FileWriter fw = new FileWriter(csv, false)) { fw.write(""); }
            catch (IOException ex) { JOptionPane.showMessageDialog(frame, "Грешка при изтриване: " + ex.getMessage(), "Грешка", JOptionPane.ERROR_MESSAGE); }
            refreshSalesArea();
        });
        salesButtons.add(clearCsvBtn);

        salesPanel.add(salesButtons, BorderLayout.SOUTH);
        tabs.addTab("Продажби", salesPanel);

        // Console
        consoleText.setBackground(new Color(255,255,255,220));
        consoleText.setOpaque(true);
        JScrollPane consoleScroll = new JScrollPane(consoleText);
        consoleScroll.setOpaque(false);
        consoleScroll.getViewport().setOpaque(false);

        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBorder(new EmptyBorder(6,6,6,6));
        consolePanel.setOpaque(false);
        consolePanel.add(consoleScroll, BorderLayout.CENTER);

        JPanel consoleButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        consoleButtons.setOpaque(false);
        JButton clearConsoleBtn = new JButton("Изчисти конзолата");
        clearConsoleBtn.addActionListener(e -> consoleText.setText(""));
        consoleButtons.add(clearConsoleBtn);
        consolePanel.add(consoleButtons, BorderLayout.SOUTH);
        tabs.addTab("Конзола", consolePanel);

        // Admin
        JPanel adminPanel = new JPanel(new BorderLayout());
        adminPanel.setOpaque(false);

        // *** НОВА ПРОМЯНА (Лейаут на Админ панел) ***
        // Променяме FlowLayout на GridLayout(0, 2), за да подредим 6-те бутона
        // в 3 реда по 2 колони (0 = auto-rows, 2 = 2 cols, 6,6 = gaps)
        JPanel adminTop = new JPanel(new GridLayout(0, 2, 6, 6));
        adminTop.setBorder(new EmptyBorder(5, 5, 5, 5)); // Добавяме малко отстояние
        adminTop.setOpaque(false);

        adminAddDrinkBtn = new JButton("Добави напитка");
        adminAddDrinkBtn.addActionListener(e -> handleAddDrinkDialog());
        adminTop.add(adminAddDrinkBtn);

        // *** НОВО *** Бутон за Редактиране
        adminEditDrinkBtn = new JButton("Редактирай напитка");
        adminEditDrinkBtn.addActionListener(e -> handleEditDrinkDialog());
        adminTop.add(adminEditDrinkBtn);

        adminDeleteDrinkBtn = new JButton("Изтрии напитка");
        adminDeleteDrinkBtn.addActionListener(e -> handleDeleteSelectedDrink());
        adminTop.add(adminDeleteDrinkBtn);

        adminSetImageBtn = new JButton("Постави изображение");
        adminSetImageBtn.addActionListener(e -> chooseImageForSelectedDrink());
        adminTop.add(adminSetImageBtn);

        adminRemoveImageBtn = new JButton("Премахни изображение");
        adminRemoveImageBtn.addActionListener(e -> removeImageForSelectedDrink());
        adminTop.add(adminRemoveImageBtn);

        adminSetBackgroundBtn = new JButton("Постави фон");
        adminSetBackgroundBtn.addActionListener(e -> chooseBackgroundImage());
        adminTop.add(adminSetBackgroundBtn);

        adminPanel.add(adminTop, BorderLayout.NORTH);
        tabs.addTab("Админ", adminPanel);

        return tabs;
    }

    /**
     * Update UI elements enabled/visible state based on isAdmin flag.
     */
    private void updateAdminState() {
        boolean enable = isAdmin;
        if (adminRefillBtn != null) adminRefillBtn.setEnabled(enable);
        if (adminCollectBtn != null) adminCollectBtn.setEnabled(enable);
        if (adminReportBtn != null) adminReportBtn.setEnabled(enable);
        if (adminAddDrinkBtn != null) adminAddDrinkBtn.setEnabled(enable);
        if (adminEditDrinkBtn != null) adminEditDrinkBtn.setEnabled(enable); // *** НОВО ***
        if (adminDeleteDrinkBtn != null) adminDeleteDrinkBtn.setEnabled(enable);
        if (adminSetImageBtn != null) adminSetImageBtn.setEnabled(enable);
        if (adminRemoveImageBtn != null) adminRemoveImageBtn.setEnabled(enable);
        if (adminSetBackgroundBtn != null) adminSetBackgroundBtn.setEnabled(enable);

        if (viewPriceBtn != null) viewPriceBtn.setVisible(enable);
        if (adminLoginBtn != null) adminLoginBtn.setText(enable ? "Изход Админ" : "Вход Админ");

        if (enable) {
            splitPane.setRightComponent(rightTabs);
            SwingUtilities.invokeLater(() -> {
                splitPane.setDividerLocation(0.55);
                splitPane.revalidate();
                splitPane.repaint();
            });
        } else {
            splitPane.setRightComponent(new JPanel());
            SwingUtilities.invokeLater(() -> {
                splitPane.setDividerLocation(1.0);
                splitPane.revalidate();
                splitPane.repaint();
            });
        }

        refreshMenuList();
    }

    /**
     * *** ПРОМЕНЕН МЕТОД (за Точка 2) ***
     * Refresh the menu list model from the simulator's menu.
     * Each entry uses the format: "Name — PRICE лв." so other code can split on " — ".
     * Вече проверява наличностите и добавя "[ИЗЧЕРПАНО]", ако напитката не може да бъде направена.
     */
    private void refreshMenuList() {
        menuListModel.clear();
        Map<String, CoffeeMachineSimulator.Drink> menu = machine.getMenu();
        List<String> names = new ArrayList<>(menu.keySet());
        Collections.sort(names);
        
        for (String name : names) {
            CoffeeMachineSimulator.Drink d = menu.get(name);
            if (d != null) {
                // *** НОВА ПРОВЕРКА ***
                // Използваме Collections.singletonList, за да проверим само за 1 брой от напитката
                boolean canMake = machine.checkTotalIngredients(Collections.singletonList(name));
                String statusTag = canMake ? "" : " [ИЗЧЕРПАНО]";
                
                menuListModel.addElement(String.format("%s — %.2f лв.%s", name, d.getPrice(), statusTag));
            }
        }
    }


    /**
     * Show the calculated ingredient cost (себестойност) for the selected drink.
     */
    private void showSelectedCost() {
        String selected = menuList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(frame, "Моля, изберете напитка.", "Инфо", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = selected.split(" — ")[0].trim();
        CoffeeMachineSimulator.Drink d = machine.getMenu().get(name);
        if (d == null) {
            JOptionPane.showMessageDialog(frame, "Напитката не е намерена: " + name, "Грешка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        double cost = 0.0;
        Map<String, Double> costs = machine.getIngredientCosts();
        for (Map.Entry<String, Integer> en : d.getIngredients().entrySet()) {
            double unit = costs.getOrDefault(en.getKey(), 0.0);
            cost += unit * en.getValue();
        }
        JOptionPane.showMessageDialog(frame, String.format("Себестойност на '%s': %.4f лв.", name, cost), "Себестойност", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleAdminToggle(String string) {
        if (isAdmin) {
            isAdmin = false;
            JOptionPane.showMessageDialog(frame, "Излезохте от режим Администратор.", "Админ", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JPasswordField pf = new JPasswordField();
            int ok = JOptionPane.showConfirmDialog(frame, pf, "Въведете парола за Администратор:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok == JOptionPane.OK_OPTION) {
                String password = new String(pf.getPassword());
                if ("Adm1n".equals(password)) {
                    isAdmin = true;
                    JOptionPane.showMessageDialog(frame, "Успешен вход като Администратор.", "Админ", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(frame, "Грешна парола.", "Админ", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        updateAdminState();
        refreshAllUI();
    }

    // ---------------- Background image handling ----------------

    private void chooseBackgroundImage() {
        if (!isAdmin) {
            JOptionPane.showMessageDialog(frame, "Тази операция е достъпна само за администратор.", "Достъп", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Изберете изображение за фон");
        int res = chooser.showOpenDialog(frame);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                JOptionPane.showMessageDialog(frame, "Файлът не е изображение.", "Грешка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String alphaStr = JOptionPane.showInputDialog(frame, "Въведете прозрачност (0.0 - 1.0), напр. 0.25:", "0.25");
            float alpha = 0.25f;
            if (alphaStr != null) {
                try {
                    alpha = Float.parseFloat(alphaStr.replace(',', '.'));
                } catch (NumberFormatException ex) {
                    alpha = 0.25f;
                }
                if (alpha < 0f) alpha = 0f;
                if (alpha > 1f) alpha = 1f;
            }
            backgroundPanel.setBackgroundImage(img, alpha);
            JOptionPane.showMessageDialog(frame, "Фонът е зададен.", "Успех", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Грешка при зареждане на изображението: " + ex.getMessage(), "Грешка", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------- Payment: Luhn, processing ----------------

    private static class PaymentResult {
        final boolean success;
        final String status; // "CASH" or "CARD"
        final double paidAmount;
        final double change;
        final String cardLast4;
        final String transactionId;
        final String timestamp;

        PaymentResult(boolean success, String status, double paidAmount, double change, String cardLast4, String txId, String ts) {
            this.success = success; this.status = status; this.paidAmount = paidAmount; this.change = change; this.cardLast4 = cardLast4; this.transactionId = txId; this.timestamp = ts;
        }

        static PaymentResult cancelled() { return new PaymentResult(false, "CANCELLED", 0, 0, null, null, null); }
    }

    private static boolean luhnCheck(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(number.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private PaymentResult processPayment(double totalCost) {
        String[] options = {"Плащане в брой  💵", "Плащане с карта  💳", "Откажи"};
        int choice = JOptionPane.showOptionDialog(frame,
                String.format("Обща цена: %.2f лв.\nИзберете метод на плащане:", totalCost),
                "Плащане",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            return PaymentResult.cancelled();
        }

        String txId = generateTransactionId();
        String timestamp = LocalDateTime.now().format(TS_FMT);

        if (choice == 0) { // cash
            while (true) {
                String moneyStr = JOptionPane.showInputDialog(frame, String.format("Обща цена: %.2f лв. Въведете внесена сума (в лв.):", totalCost), String.format("%.2f", totalCost));
                if (moneyStr == null) return PaymentResult.cancelled();
                double money;
                try { money = Double.parseDouble(moneyStr.replace(',', '.')); } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Невалиден формат за сума. Моля, въведете число.", "Грешка", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                if (money < totalCost) {
                    JOptionPane.showMessageDialog(frame, String.format("Недостатъчно пари. Нужни: %.2f лв.", totalCost), "Грешка", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                double change = money - totalCost;
                JOptionPane.showMessageDialog(frame, String.format("Плащането е успешно.\nПлатено: %.2f лв.\nЦена: %.2f лв.\nРесто: %.2f лв.", money, totalCost, change), "Плащане успешно", JOptionPane.INFORMATION_MESSAGE);
                return new PaymentResult(true, "CASH", money, change, null, txId, timestamp);
            }
        } else { // card
            JPanel cardPanel = new JPanel(new GridLayout(4, 2, 6, 6));
            JTextField cardNumber = new JTextField();
            JTextField expiry = new JTextField(); // MM/YY
            JTextField cvv = new JTextField();
            JTextField nameOnCard = new JTextField();

            cardNumber.setToolTipText("Пример: 4242 4242 4242 4242");
            expiry.setToolTipText("MM/YY, пример: 12/30");
            cvv.setToolTipText("3 цифри, пример: 123");
            nameOnCard.setToolTipText("Име на картодържателя (пример: Test User)");

            cardPanel.add(new JLabel("Име на картодържателя:")); cardPanel.add(nameOnCard);
            cardPanel.add(new JLabel("Номер на карта (13-19 цифри):")); cardPanel.add(cardNumber);
            cardPanel.add(new JLabel("Валидност (MM/YY):")); cardPanel.add(expiry);
            cardPanel.add(new JLabel("CVV (3 цифри):")); cardPanel.add(cvv);

            int res = JOptionPane.showConfirmDialog(frame, cardPanel, "Плащане с карта (Това е само тестов симулатор)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return PaymentResult.cancelled();

            String num = cardNumber.getText().trim().replaceAll("\\s+","");
            String exp = expiry.getText().trim();
            String c = cvv.getText().trim();
            String holder = nameOnCard.getText().trim();

            if (holder.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Името е задължително.", "Грешка", JOptionPane.ERROR_MESSAGE);
                return PaymentResult.cancelled();
            }
            if (!num.matches("\\d{13,19}")) {
                JOptionPane.showMessageDialog(frame, "Невалиден картов номер.", "Грешка", JOptionPane.ERROR_MESSAGE);
                return PaymentResult.cancelled();
            }
            if (!luhnCheck(num)) {
                JOptionPane.showMessageDialog(frame, "Картовият номер не преминава Luhn проверка.", "Грешка", JOptionPane.ERROR_MESSAGE);
                return PaymentResult.cancelled();
            }
            if (!exp.matches("(0[1-9]|1[0-2])/(\\d{2})")) {
                JOptionPane.showMessageDialog(frame, "Невалиден формат валидност (MM/YY).", "Грешка", JOptionPane.ERROR_MESSAGE);
                return PaymentResult.cancelled();
            }
            if (!c.matches("\\d{3}")) {
                JOptionPane.showMessageDialog(frame, "Невалиден CVV.", "Грешка", JOptionPane.ERROR_MESSAGE);
                return PaymentResult.cancelled();
            }

            final JDialog wait = new JDialog(frame, "Обработка на картата...", true);
            JPanel p = new JPanel(new BorderLayout());
            p.add(new JLabel("Свързване с платежен процесор... Моля изчакайте."), BorderLayout.CENTER);
            wait.getContentPane().add(p);
            wait.setSize(350,120);
            wait.setLocationRelativeTo(frame);

            SwingWorker<Boolean,Void> worker = new SwingWorker<>() {
                @Override protected Boolean doInBackground() throws Exception {
                    Thread.sleep(700 + new Random().nextInt(900));
                    return true; // simulated approval
                }
                @Override protected void done() { wait.dispose(); }
            };
            worker.execute();
            wait.setVisible(true);

            try {
                boolean approved = worker.get();
                if (approved) {
                    String last4 = num.substring(num.length()-4);
                    JOptionPane.showMessageDialog(frame, String.format("Плащането с карта е успешно.\nЦена: %.2f лв.\n(•••• %s)", totalCost, last4), "Плащане успешно", JOptionPane.INFORMATION_MESSAGE);
                    return new PaymentResult(true, "CARD", totalCost, 0.0, last4, generateTransactionId(), LocalDateTime.now().format(TS_FMT));
                } else {
                    JOptionPane.showMessageDialog(frame, "Плащането е отказано.", "Отказ", JOptionPane.ERROR_MESSAGE);
                    return PaymentResult.cancelled();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Грешка при обработката: " + ex.getMessage(), "Грешка", JOptionPane.ERROR_MESSAGE);
                return PaymentResult.cancelled();
            }
        }
    }

    private String generateTransactionId() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i=0;i<10;i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    // ---------------- Orders and receipts ----------------

    /**
     * *** ПРОМЕНЕН МЕТОД (за Точка 4) ***
     */
    private void handleQuickOrder(JList<String> orderList) {
        List<String> selections = orderList.getSelectedValuesList();
        if (selections.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Моля, изберете поне една напитка.", "Поръчка", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<String> names = new ArrayList<>();
        double totalCost = 0.0;
        for (String s : selections) {
            String name = s.split(" — ")[0].trim();
            CoffeeMachineSimulator.Drink d = machine.getMenu().get(name);
            if (d == null) {
                JOptionPane.showMessageDialog(frame, "Напитката не е намерена: " + name, "Грешка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            names.add(name);
            totalCost += d.getPrice();
        }

        if (!machine.checkTotalIngredients(names)) {
            JOptionPane.showMessageDialog(frame, "Недостатъчно съставки за поръчката.", "Грешка", JOptionPane.ERROR_MESSAGE);
            refreshInventoryArea();
            return;
        }

        PaymentResult pay = processPayment(totalCost);
        if (!pay.success) return;

        // *** НОВА ПРОМЯНА (за Точка 4) ***
        // Старите 4 реда са заменени с извикване на новия метод
        runPreparationAndReceipt(names, totalCost, pay);
    }

    /**
     * *** ПРОМЕНЕН МЕТОД (за Точка 4) ***
     */
    private void handleOrderWithQuantities(JList<String> orderList) {
        List<String> selections = orderList.getSelectedValuesList();
        if (selections.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Моля, изберете поне една напитка.", "Поръчка", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        LinkedHashMap<String,Integer> selectedMap = new LinkedHashMap<>();
        for (String s : selections) {
            String name = s.split(" — ")[0].trim();
            selectedMap.putIfAbsent(name, 1);
        }

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST; gbc.insets = new Insets(4,4,4,4);

        Map<String,JSpinner> spinnerMap = new LinkedHashMap<>();
        for (String name : selectedMap.keySet()) {
            panel.add(new JLabel(name), gbc);
            gbc.gridx = 1;
            SpinnerNumberModel model = new SpinnerNumberModel(1, 1, 99, 1);
            JSpinner spinner = new JSpinner(model);
            panel.add(spinner, gbc);
            spinnerMap.put(name, spinner);
            gbc.gridx = 0; gbc.gridy++;
        }

        int res = JOptionPane.showConfirmDialog(frame, panel, "Задайте количества за избраните напитки", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        List<String> orderedNames = new ArrayList<>();
        double totalCost = 0.0;
        for (Map.Entry<String,JSpinner> en : spinnerMap.entrySet()) {
            String name = en.getKey();
            int qty = (Integer) en.getValue().getValue();
            CoffeeMachineSimulator.Drink d = machine.getMenu().get(name);
            if (d == null) {
                JOptionPane.showMessageDialog(frame, "Напитката не е намерена: " + name, "Грешка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            for (int i=0;i<qty;i++) orderedNames.add(name);
            totalCost += d.getPrice() * qty;
        }

        if (!machine.checkTotalIngredients(orderedNames)) {
            JOptionPane.showMessageDialog(frame, "Недостатъчно съставки за поръчката.", "Грешка", JOptionPane.ERROR_MESSAGE);
            refreshInventoryArea();
            return;
        }

        PaymentResult pay = processPayment(totalCost);
        if (!pay.success) return;

        // *** НОВА ПРОМЯНА (за Точка 4) ***
        // Старите 4 реда са заменени с извикване на новия метод
        runPreparationAndReceipt(orderedNames, totalCost, pay);
    }

    /**
     * *** НОВ МЕТОД (за Точка 4) ***
     * Показва "моля изчакайте" диалог, докато симулира приготвянето на напитките във фонов режим.
     * След приключване, затваря диалога и показва квитанцията.
     *
     * @param names Списък с имената на всички поръчани напитки (напр. ["Espresso", "Espresso", "Latte"])
     * @param totalCost Обща цена на поръчката
     * @param pay Резултатът от плащането
     */
    private void runPreparationAndReceipt(List<String> names, double totalCost, PaymentResult pay) {
        
        // 1. Създаване на диалога "Моля изчакайте"
        final JDialog waitDialog = new JDialog(frame, "Приготвяне...", true); // true = модален
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        p.add(new JLabel("☕️... напитките се приготвят... Моля изчакайте."), BorderLayout.CENTER);
        waitDialog.getContentPane().add(p);
        waitDialog.setSize(350, 120);
        waitDialog.setLocationRelativeTo(frame);
        waitDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // Потребителят не може да го затвори
        
        // 2. Създаване на SwingWorker за фоновата работа
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            
            @Override
            protected Void doInBackground() throws Exception {
                // Симулиране на забавяне
                long delayPerDrink = 500; // 0.5 секунди на напитка
                long baseDelay = 1000;    // 1 секунда основа
                long totalDelay = baseDelay + (names.size() * delayPerDrink);
                
                Thread.sleep(totalDelay);
                
                // Изпълнение на същинската работа (консумация на инвентар)
                for (String nm : names) {
                    machine.makeSingleDrink(nm);
                }
                return null; // Не връщаме нищо
            }
            
            @Override
            protected void done() {
                // Този код се изпълнява на EDT, СЛЕД като doInBackground() приключи
                waitDialog.dispose(); // Затваряме диалога "Моля изчакайте"
                
                try {
                    get(); // Проверяваме за грешки от фоновия процес
                    
                    // Извикваме останалата част от логиката
                    showReceipt(names, totalCost, pay);
                    writeTransactionToCsv(pay, names, totalCost);
                    refreshAllUI();
                    
                } catch (Exception e) {
                    // Ако има грешка при приготвянето
                    e.printStackTrace(); // Ще се покаже в конзолата
                    JOptionPane.showMessageDialog(frame, "Възникна грешка при приготвяне: " + e.getMessage(), "Грешка", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        // 3. Стартиране на worker-а и показване на диалога
        worker.execute();
        waitDialog.setVisible(true); // Това ще блокира, докато worker-ът не извика dispose()
    }


    private void showReceipt(List<String> orderedNames, double totalCost, PaymentResult pay) {
        String receipt = generateReceiptText(orderedNames, totalCost, pay);

        JTextArea receiptArea = new JTextArea(receipt);
        receiptArea.setEditable(false);
        receiptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(receiptArea);
        scroll.setPreferredSize(new Dimension(520, 360));

        JButton printBtn = new JButton("Принтирай 🖨️");
        JButton saveBtn = new JButton("Запази като файл");
        JButton emailBtn = new JButton("Изпрати по имейл ✉️");
        JButton closeBtn = new JButton("Затвори");

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(printBtn);
        toolBar.add(saveBtn);
        toolBar.add(emailBtn);
        toolBar.addSeparator();
        toolBar.add(closeBtn);

        final JDialog dlg = new JDialog(frame, "Квитанция", true);
        dlg.getContentPane().setLayout(new BorderLayout(8,8));
        dlg.getContentPane().add(toolBar, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8,8));
        center.setOpaque(true);
        center.add(scroll, BorderLayout.CENTER);

        dlg.getContentPane().add(center, BorderLayout.CENTER);
        dlg.pack();
        dlg.setLocationRelativeTo(frame);

        printBtn.addActionListener(e -> {
            try {
                boolean done = receiptArea.print();
                if (!done) JOptionPane.showMessageDialog(dlg, "Печатът беше отменен или няма принтер.", "Печат", JOptionPane.ERROR_MESSAGE);
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(dlg, "Грешка при печат: " + ex.getMessage(), "Печат", JOptionPane.ERROR_MESSAGE);
            }
        });

        saveBtn.addActionListener(e -> saveReceiptToFile(receipt, dlg));
        emailBtn.addActionListener(e -> sendReceiptByEmail(receipt, pay));
        closeBtn.addActionListener(e -> dlg.dispose());

        dlg.setVisible(true);
    }

    private String generateReceiptText(List<String> orderedNames, double totalCost, PaymentResult pay) {
        StringBuilder sb = new StringBuilder();
        String now = (pay != null && pay.timestamp != null) ? pay.timestamp : LocalDateTime.now().format(TS_FMT);
        sb.append("====== Coffee Machine Receipt ======\n");
        sb.append("Търговец: ЕТ КРУМ КРУМОВ\n");
        sb.append("Адрес: ул. Стефан Сливков 7, град Стара Загора\n");
        sb.append("Тел: +359 2 123 456\n");
        sb.append(String.format("Дата/час: %s\n", now));
        sb.append(String.format("Транзакция ID: %s\n", pay != null && pay.transactionId != null ? pay.transactionId : "-"));
        sb.append("------------------------------------\n");

        Map<String,Integer> counts = new LinkedHashMap<>();
        for (String n : orderedNames) counts.put(n, counts.getOrDefault(n, 0) + 1);

        for (Map.Entry<String,Integer> e : counts.entrySet()) {
            String name = e.getKey();
            int qty = e.getValue();
            CoffeeMachineSimulator.Drink d = machine.getMenu().get(name);
            double price = (d != null) ? d.getPrice() : 0.0;
            sb.append(String.format("%-20s x%2d  %6.2f лв.\n", name, qty, price * qty));
        }

        sb.append("------------------------------------\n");
        sb.append(String.format("Обща цена:           %8.2f лв.\n", totalCost));

        if (pay != null) {
            String methodLabel = "Неизвестен";
            String symbol = "";
            if ("CASH".equals(pay.status)) { methodLabel = "В брой (Cash)"; symbol = "💵"; }
            else if ("CARD".equals(pay.status)) { methodLabel = "С карта (Card)"; symbol = "💳"; }
            sb.append(String.format("Платено (%s):       %8.2f лв.\n", methodLabel + " " + symbol, pay.paidAmount));
            sb.append(String.format("Ресто:               %8.2f лв.\n", pay.change));
            if (pay.cardLast4 != null) sb.append(String.format("Детайли карта:      ▪▪▪▪ %s\n", pay.cardLast4));
        }

        sb.append("------------------------------------\n");
        sb.append("Благодарим Ви! Посетете ни пак.\n");
        sb.append("====================================\n");

        return sb.toString();
    }

    private void saveReceiptToFile(String receiptText, Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Запази квитанция като...");
        chooser.setSelectedFile(new File("receipt.txt"));
        int res = chooser.showSaveDialog(parent);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(receiptText);
            JOptionPane.showMessageDialog(parent, "Квитанцията е записана: " + f.getAbsolutePath(), "Запазено", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Грешка при запис: " + ex.getMessage(), "Грешка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendReceiptByEmail(String receiptText, PaymentResult pay) {
        try {
            String subject = "Квитанция от Coffee Machine - " + (pay != null && pay.transactionId != null ? pay.transactionId : "");
            String body = receiptText;
            String uriStr = String.format("mailto:?subject=%s&body=%s",
                    URLEncoder.encode(subject, StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(body, StandardCharsets.UTF_8.toString()));
            Desktop.getDesktop().mail(new URI(uriStr));
        } catch (Exception ex) {
            int ans = JOptionPane.showConfirmDialog(frame, "Неуспешно стартиране на имейл клиент: " + ex.getMessage() + ". Желаете ли да запишете квитанцията като файл вместо това?", "Имейл", JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) saveReceiptToFile(receiptText, frame);
        }
    }

    // ---------------- Inventory / Sales refresh ----------------

    /**
     * *** ПРОМЕНЕН МЕТОД ***
     * Вече показва сигнал "*** МАЛКО ***" за съставки под LOW_STOCK_THRESHOLD
     */
    private void refreshInventoryArea() {
        StringBuilder sb = new StringBuilder();
        Map<String,Integer> inv = machine.getInventory();
        Map<String,Double> costs = machine.getIngredientCosts();
        List<String> keys = new ArrayList<>(inv.keySet());
        Collections.sort(keys);
        
        for (String k : keys) {
            int qty = inv.get(k);
            double cost = costs.getOrDefault(k, 0.0);
            sb.append(String.format("%-25s : %6d (Цена/единица: %.4f)", k, qty, cost));
            
            // *** НОВА ПРОВЕРКА ***
            if (qty < LOW_STOCK_THRESHOLD) {
                sb.append(" *** МАЛКО ***\n");
            } else {
                sb.append("\n");
            }
        }
        
        sb.append("\nКаса (бруто): ").append(String.format("%.2f лв.", machine.getCashAmount()));
        sb.append("\nОбща печалба: ").append(String.format("%.2f лв.", machine.getTotalProfitAmount()));
        if (inventoryText != null) {
             inventoryText.setText(sb.toString());
             inventoryText.setCaretPosition(0);
        }
    }

    private void refreshSalesArea() {
        StringBuilder sb = new StringBuilder();
        File csv = new File("sales_log.csv");
        if (csv.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(csv))) {
                String line;
                boolean first = true;
                List<String> lines = new ArrayList<>();
                while ((line = br.readLine()) != null) {
                    if (first) { first = false; continue; }
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(",", 5);
                    if (parts.length >= 5) {
                        String ts = parts[0];
                        String drink = parts[1];
                        String price = parts[2];
                        String profit = parts[4];
                        lines.add(String.format("[%s] %s (Цена: %s лв., Печалба: %s лв.)", ts, drink, price, profit));
                    } else {
                        lines.add(line);
                    }
                }
                for (int i = lines.size() - 1; i >= 0; i--) sb.append(lines.get(i)).append("\n");
            } catch (IOException ex) {
                sb.append("Грешка при четене на sales_log.csv: ").append(ex.getMessage()).append("\n");
            }
        } else {
            List<CoffeeMachineSimulator.SaleLog> sales = machine.getSalesHistory();
            if (sales == null || sales.isEmpty()) {
                sb.append("Няма регистрирани продажби.\n");
            } else {
                for (int i = sales.size() - 1; i >= 0; i--) {
                    sb.append(sales.get(i).toString()).append("\n");
                }
            }
        }
        if (salesText != null) {
             salesText.setText(sb.toString());
             salesText.setCaretPosition(0);
        }
    }

    private void writeTransactionToCsv(PaymentResult pay, List<String> items, double totalCost) {
        if (pay == null || !pay.success) return;
        File f = new File("sales_log.csv");
        boolean exists = f.exists();
        double totalIngredientCost = 0.0;
        Map<String, Double> unitCosts = machine.getIngredientCosts();
        for (String name : items) {
            CoffeeMachineSimulator.Drink d = machine.getMenu().get(name);
            if (d == null) continue;
            for (Map.Entry<String, Integer> en : d.getIngredients().entrySet()) {
                totalIngredientCost += unitCosts.getOrDefault(en.getKey(), 0.0) * en.getValue();
            }
        }
        double profit = totalCost - totalIngredientCost;

        try (FileWriter fw = new FileWriter(f, true)) {
            if (!exists) {
                fw.write("timestamp,txid,items,total,paid,change,method,cardLast4,profit\n");
            }
            String ts = LocalDateTime.now().format(TS_FMT);
            String tx = pay.transactionId != null ? pay.transactionId : "-";
            String joined = String.join(";", items).replace(",", " ").replace("\n", " ");
            String method = pay.status != null ? pay.status : "";
            String paid = PRICE_FMT.format(pay.paidAmount);
            String change = PRICE_FMT.format(pay.change);
            String total = PRICE_FMT.format(totalCost);
            String prof = PRICE_FMT.format(profit);
            String card4 = pay.cardLast4 != null ? pay.cardLast4 : "";
            fw.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n", ts, tx, joined, total, paid, change, method, card4, prof));
        } catch (IOException ex) {
            System.err.println("Failed to write transaction CSV: " + ex.getMessage());
        }
    }

    // ---------------- Menu image management ----------------

    private void updateDisplayedImageForSelectedMenuItem() {
        String selected = null;

        try {
            if (leftTabs != null && leftTabs.getSelectedIndex() == 1) { 
                selected = orderList.getSelectedValue(); 
            } else {
                selected = menuList.getSelectedValue();
            }
        } catch (Exception ex) {
            System.err.println("Error reading list selection: " + ex.getMessage());
        }

        if (selected == null) {
            imageLabel.setIcon(null);
            imageLabel.setText("Няма избрана напитка");
            return;
        }
        
        String name = selected.split(" — ")[0].trim();
        String path = machine.getDrinkImage(name);
        
        if (path == null || path.trim().isEmpty()) {
            imageLabel.setIcon(null);
            imageLabel.setText("<Няма изображение за " + name + ">");
        } else {
            setImageToLabelFromPath(path);
            imageLabel.setToolTipText(path);
        }
    }


    private void setImageToLabelFromPath(String path) {
        File f = new File(path);
        if (!f.exists()) {
            imageLabel.setIcon(null);
            imageLabel.setText("<Файлът не е намерен>");
            return;
        }
        String low = path.toLowerCase(Locale.ROOT);
        try {
            if (low.endsWith(".gif")) {
                ImageIcon icon = new ImageIcon(path);
                imageLabel.setIcon(icon);
                imageLabel.setText(null);
                return;
            }

            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                imageLabel.setIcon(null);
                imageLabel.setText("<Грешка при зареждане>");
                return;
            }
            Dimension size = imageLabel.getPreferredSize();
            int targetW = size.width;
            int targetH = size.height;
            double imgW = img.getWidth();
            double imgH = img.getHeight();
            double scale = Math.min((double) targetW / imgW, (double) targetH / imgH);
            int newW = Math.max(1, (int) (imgW * scale));
            int newH = Math.max(1, (int) (imgH * scale));
            Image scaled = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaled));
            imageLabel.setText(null);
        } catch (IOException ex) {
            imageLabel.setIcon(null);
            imageLabel.setText("<Грешка: " + ex.getMessage() + ">");
        }
    }

    private void chooseImageForSelectedDrink() {
        if (!isAdmin) {
            JOptionPane.showMessageDialog(frame, "Тази операция е достъпна само за администратор.", "Достъп", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        List<String> selected = menuList.getSelectedValuesList(); 
        if (selected.isEmpty()) {
             if (orderList.getSelectedValue() != null) {
                 selected = orderList.getSelectedValuesList();
             } else {
                 JOptionPane.showMessageDialog(frame, "Моля, изберете напитка.", "Инфо", JOptionPane.INFORMATION_MESSAGE);
                 return;
             }
        }
        
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) return;
        File chosen = chooser.getSelectedFile();
        try {
            BufferedImage img = ImageIO.read(chosen);
            if (img == null) { JOptionPane.showMessageDialog(frame, "Файлът не е изображение.", "Грешка", JOptionPane.ERROR_MESSAGE); return; }
            if (selected.size() > 1) {
                int applyAll = JOptionPane.showConfirmDialog(frame, "Прикачване към всички избрани напитки?", "Потвърждение", JOptionPane.YES_NO_CANCEL_OPTION);
                if (applyAll == JOptionPane.CANCEL_OPTION || applyAll == JOptionPane.CLOSED_OPTION) return;
                if (applyAll == JOptionPane.YES_OPTION) {
                    for (String s : selected) machine.setDrinkImage(s.split(" — ")[0].trim(), chosen.getAbsolutePath());
                    setImageToLabelFromPath(chosen.getAbsolutePath());
                    JOptionPane.showMessageDialog(frame, "Изображението е прикачено.", "Успех", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    String name = selected.get(0).split(" — ")[0].trim();
                    machine.setDrinkImage(name, chosen.getAbsolutePath());
                    setImageToLabelFromPath(chosen.getAbsolutePath());
                    JOptionPane.showMessageDialog(frame, "Изображението е прикачено към " + name + ".", "Успех", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                String name = selected.get(0).split(" — ")[0].trim();
                machine.setDrinkImage(name, chosen.getAbsolutePath());
                setImageToLabelFromPath(chosen.getAbsolutePath());
                JOptionPane.showMessageDialog(frame, "Изображението е прикачено към " + name + ".", "Успех", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Грешка при зареждане: " + ex.getMessage(), "Грешка", JOptionPane.ERROR_MESSAGE);
        }
        refreshAllUI();
    }

    private void removeImageForSelectedDrink() {
        if (!isAdmin) {
            JOptionPane.showMessageDialog(frame, "Тази операция е достъпна само за администратор.", "Достъп", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        List<String> selected = menuList.getSelectedValuesList();
        if (selected.isEmpty()) {
             if (orderList.getSelectedValue() != null) {
                 selected = orderList.getSelectedValuesList();
             } else {
                JOptionPane.showMessageDialog(frame, "Моля, изберете напитка.", "Инфо", JOptionPane.INFORMATION_MESSAGE);
                return;
             }
        }
        
        if (selected.size() > 1) {
            int ans = JOptionPane.showConfirmDialog(frame, "Премахване на изображенията за всички избрани напитки?", "Потвърждение", JOptionPane.YES_NO_OPTION);
            if (ans != JOptionPane.YES_OPTION) return;
            for (String s : selected) machine.setDrinkImage(s.split(" — ")[0].trim(), null);
            updateDisplayedImageForSelectedMenuItem();
            JOptionPane.showMessageDialog(frame, "Изображенията са премахнати.", "Успех", JOptionPane.INFORMATION_MESSAGE);
        } else {
            String name = selected.get(0).split(" — ")[0].trim();
            machine.setDrinkImage(name, null);
            updateDisplayedImageForSelectedMenuItem();
            JOptionPane.showMessageDialog(frame, "Изображението за " + name + " е премахнато.", "Успех", JOptionPane.INFORMATION_MESSAGE);
        }
        refreshAllUI();
    }

    // ---------------- Admin utilities ----------------

    /**
     * *** НОВ МЕТОД (Private Helper) ***
     * Показва диалог за добавяне/редактиране на напитка.
     * Ако drinkToEdit е null, работи в режим "Добавяне".
     * Ако не е null, попълва полетата с данните на напитката за "Редактиране".
     * Връща Map с данните, ако потребителят натисне "OK", или null при "Cancel".
     */
    private Map<String, Object> showDrinkEditorDialog(String title, CoffeeMachineSimulator.Drink drinkToEdit) {
        JTextField nameField = new JTextField();
        JTextField priceField = new JTextField();
        JTextArea ingredArea = new JTextArea(6, 30);
        ingredArea.setLineWrap(true);
        ingredArea.setWrapStyleWord(true);
        
        String instr = "Въведете съставките в отделни редове във формат: Име:количество\nПример:\nМляко (мл):150\nЗахар (гр):10";
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new GridLayout(2, 2, 6, 6));
        top.add(new JLabel("Име на напитка:")); top.add(nameField);
        top.add(new JLabel("Цена (напр. 3.50):")); top.add(priceField);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JLabel(instr), BorderLayout.CENTER);
        panel.add(new JScrollPane(ingredArea), BorderLayout.SOUTH);

        // Ако сме в режим "Редактиране", попълни полетата
        if (drinkToEdit != null) {
            nameField.setText(drinkToEdit.getName());
            // Използвай Locale.US, за да гарантираш, че 3.50 е с точка, а не запетая
            priceField.setText(String.format(Locale.US, "%.2f", drinkToEdit.getPrice())); 
            
            StringBuilder sbIng = new StringBuilder();
            for (Map.Entry<String, Integer> entry : drinkToEdit.getIngredients().entrySet()) {
                sbIng.append(String.format("%s:%d\n", entry.getKey(), entry.getValue()));
            }
            ingredArea.setText(sbIng.toString());
        }

        int res = JOptionPane.showConfirmDialog(frame, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) {
            return null; // Потребителят е натиснал "Cancel"
        }

        // Ако потребителят натисне "OK", събери данните
        try {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Името не може да е празно.", "Грешка", JOptionPane.ERROR_MESSAGE);
                return null;
            }

            double price = Double.parseDouble(priceField.getText().trim().replace(',', '.'));
            if (price <= 0) {
                 JOptionPane.showMessageDialog(frame, "Цената трябва да е положително число.", "Грешка", JOptionPane.ERROR_MESSAGE);
                return null;
            }

            String[] lines = ingredArea.getText().split("\\r?\\n");
            Map<String, Integer> ingredients = new HashMap<>();
            for (String line : lines) {
                line = line.trim(); if (line.isEmpty()) continue;
                String[] parts = line.split(":");
                if (parts.length != 2) { 
                    JOptionPane.showMessageDialog(frame, "Грешен формат в съставките: " + line, "Грешка", JOptionPane.ERROR_MESSAGE); 
                    return null; 
                }
                String iname = parts[0].trim();
                int qty = Integer.parseInt(parts[1].trim());
                if (qty <= 0) {
                     JOptionPane.showMessageDialog(frame, "Количеството за " + iname + " трябва да е положително.", "Грешка", JOptionPane.ERROR_MESSAGE);
                     return null;
                }
                ingredients.put(iname, qty);
            }

            if (ingredients.isEmpty()) {
                 JOptionPane.showMessageDialog(frame, "Напитката трябва да има поне една съставка.", "Грешка", JOptionPane.ERROR_MESSAGE);
                 return null;
            }

            // Върни данните
            Map<String, Object> result = new HashMap<>();
            result.put("name", name);
            result.put("price", price);
            result.put("ingredients", ingredients);
            return result;

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Невалиден формат за цена или количество.", "Грешка", JOptionPane.ERROR_MESSAGE);
            return null;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Грешка при обработка на данните: " + ex.getMessage(), "Грешка", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }


    /**
     * *** ПРОМЕНЕН МЕТОД ***
     * Вече използва новия showDrinkEditorDialog за добавяне на напитка.
     */
    private void handleAddDrinkDialog() {
        if (!isAdmin) { 
            JOptionPane.showMessageDialog(frame, "Тази операция е достъпна само за администратор.", "Достъп", JOptionPane.ERROR_MESSAGE); 
            return; 
        }

        // Извикваме новия диалог в режим "Добавяне" (null)
        Map<String, Object> result = showDrinkEditorDialog("Добави напитка", null);

        if (result != null) {
            String name = (String) result.get("name");
            double price = (Double) result.get("price");
            Map<String, Integer> ingredients = (Map<String, Integer>) result.get("ingredients");

            machine.addDrink(name, price, ingredients);
            JOptionPane.showMessageDialog(frame, "Напитката е добавена (ако всички съставки са познати).", "Успех", JOptionPane.INFORMATION_MESSAGE);
            refreshAllUI();
        }
    }

    /**
     * *** НОВ МЕТОД ***
     * Показва диалог за редактиране на избраната напитка.
     */
    private void handleEditDrinkDialog() {
        if (!isAdmin) { 
            JOptionPane.showMessageDialog(frame, "Тази операция е достъпна само за администратор.", "Достъп", JOptionPane.ERROR_MESSAGE); 
            return; 
        }
        
        String selected = menuList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(frame, "Моля, изберете напитка от менюто за редактиране.", "Инфо", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String originalName = selected.split(" — ")[0].trim();
        CoffeeMachineSimulator.Drink drinkToEdit = machine.getMenu().get(originalName);

        if (drinkToEdit == null) {
             JOptionPane.showMessageDialog(frame, "Грешка: Напитката '" + originalName + "' не беше намерена.", "Грешка", JOptionPane.ERROR_MESSAGE);
             return;
        }

        // Извикваме новия диалог в режим "Редактиране"
        Map<String, Object> result = showDrinkEditorDialog("Редактирай напитка: " + originalName, drinkToEdit);

        if (result != null) {
            String newName = (String) result.get("name");
            double newPrice = (Double) result.get("price");
            Map<String, Integer> newIngredients = (Map<String, Integer>) result.get("ingredients");

            // Симулиране на "update" чрез delete + add
            machine.deleteDrink(originalName);
            machine.addDrink(newName, newPrice, newIngredients);

            JOptionPane.showMessageDialog(frame, "Напитката '" + originalName + "' беше успешно редактирана.", "Успех", JOptionPane.INFORMATION_MESSAGE);
            refreshAllUI();
        }
    }


    private void handleDeleteSelectedDrink() {
        if (!isAdmin) { JOptionPane.showMessageDialog(frame, "Тази операция е достъпна само за администратор.", "Достъп", JOptionPane.ERROR_MESSAGE); return; }
        String selected = menuList.getSelectedValue();
        if (selected == null) { JOptionPane.showMessageDialog(frame, "Моля, изберете напитка от менюто за изтриване.", "Инфо", JOptionPane.INFORMATION_MESSAGE); return; }
        String name = selected.split(" — ")[0].trim();
        int ans = JOptionPane.showConfirmDialog(frame, "Сигурни ли сте, че искате да изтриете: " + name + " ?", "Потвърждение", JOptionPane.YES_NO_OPTION);
        if (ans == JOptionPane.YES_OPTION) {
            machine.deleteDrink(name);
            refreshAllUI();
        }
    }

    private void handleRefillDialog() {
        if (!isAdmin) { JOptionPane.showMessageDialog(frame, "Тази операция е достъпна само за администратор.", "Достъп", JOptionPane.ERROR_MESSAGE); return; }
        Map<String, Integer> inv = machine.getInventory();
        List<String> ingredients = new ArrayList<>(inv.keySet());
        Collections.sort(ingredients);
        String choice = (String) JOptionPane.showInputDialog(frame, "Изберете съставка:", "Зареждане", JOptionPane.PLAIN_MESSAGE, null, ingredients.toArray(), ingredients.get(0));
        if (choice == null) return;
        String amountStr = JOptionPane.showInputDialog(frame, "Въведете количество за добавяне (цяло число):", "100");
        if (amountStr == null) return;
        int amount;
        try { amount = Integer.parseInt(amountStr.trim()); }
        catch (NumberFormatException ex) { JOptionPane.showMessageDialog(frame, "Невалидно количество.", "Грешка", JOptionPane.ERROR_MESSAGE); return; }
        
        if (amount <= 0) {
             JOptionPane.showMessageDialog(frame, "Количеството трябва да е положително число.", "Грешка", JOptionPane.ERROR_MESSAGE); return;
        }

        machine.refillInventory(choice, amount);
        JOptionPane.showMessageDialog(frame, String.format("Добавени %d на %s.", amount, choice), "Успех", JOptionPane.INFORMATION_MESSAGE);
        refreshAllUI();
    }

    private void handleCollectCash() {
        if (!isAdmin) { JOptionPane.showMessageDialog(frame, "Тази операция е достъпна само за администратор.", "Достъп", JOptionPane.ERROR_MESSAGE); return; }
        double collected = machine.collectCash();
        JOptionPane.showMessageDialog(frame, String.format("Изтеглени %.2f лв. от касата.", collected), "Каса", JOptionPane.INFORMATION_MESSAGE);
        refreshAllUI();
    }

    // ---------------- Utilities ----------------

    private void redirectSystemStreamsToConsole(JTextArea ta) {
        PrintStream ps = new PrintStream(new TextAreaOutputStream(ta), true);
        System.setOut(ps);
        System.setErr(ps);
    }

    private static class TextAreaOutputStream extends OutputStream {
        private final JTextArea textArea;
        private final StringBuilder buffer = new StringBuilder();

        public TextAreaOutputStream(JTextArea ta) {
            this.textArea = ta;
        }

        @Override public synchronized void write(int b) {
            buffer.append((char) b);
            if (b == '\n') flushBufferToTextArea();
        }

        @Override public synchronized void write(byte[] b, int off, int len) {
            buffer.append(new String(b, off, len));
            if (buffer.indexOf("\n") >= 0) flushBufferToTextArea();
        }

        @Override public synchronized void flush() {
            if (buffer.length() > 0) flushBufferToTextArea();
        }

        private void flushBufferToTextArea() {
            final String text = buffer.toString();
            buffer.setLength(0);
            SwingUtilities.invokeLater(() -> {
                textArea.append(text);
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
        }
    }

    // ---------------- BackgroundPanel ----------------

    private static class BackgroundPanel extends JPanel {
        private BufferedImage bg;
        private float alpha = 0.25f;

        public BackgroundPanel() {
            setOpaque(true);
        }

        public void setBackgroundImage(BufferedImage image, float alpha) {
            this.bg = image;
            this.alpha = Math.max(0f, Math.min(1f, alpha));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (bg != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w = getWidth();
                int h = getHeight();
                double imgW = bg.getWidth();
                double imgH = bg.getHeight();
                double scale = Math.max((double) w / imgW, (double) h / imgH); // cover
                int newW = (int) (imgW * scale);
                int newH = (int) (imgH * scale);
                int x = (w - newW) / 2;
                int y = (h - newH) / 2;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.drawImage(bg, x, y, newW, newH, null);
                g2.dispose();
            }
        }
    }

    // ---------------- Main ----------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CoffeeMachineUI());
    }
}