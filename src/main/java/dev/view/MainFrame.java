package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import dev.App;
import dev.controller.PasswordManager;
import dev.model.PasswordEntry;
import dev.persistence.AppPrefs;
import dev.persistence.EntryCsv;
import dev.persistence.VaultException;
import dev.model.EntryQuery;
import dev.security.PasswordAudit;
import dev.security.VaultHealth;
import dev.security.SecureClipboard;
import dev.security.Totp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Predicate;

public class MainFrame extends JFrame implements DetailPanel.Actions {
    private static final String CARD_TABLE = "table", CARD_EMPTY = "empty", CARD_NO_RESULTS = "noresults", CARD_HEALTH = "health";

    private final PasswordManager manager;
    private final PasswordTableModel tableModel;
    private final EntryTable table;
    private final Sidebar sidebar;
    private final DetailPanel detail;
    private final JTextField searchField = new JTextField();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JButton themeButton;
    private final HealthView healthView;
    private final SyncButton syncButton;
    private final JLabel countLabel = Ui.eyebrow(" ");
    private final FilterChip chipWeak = new FilterChip("Débiles"), chipReused = new FilterChip("Repetidas"),
            chipOld = new FilterChip("Antiguas"), chip2fa = new FilterChip("2FA"), chipFav = new FilterChip("Favoritas");
    private boolean syncingChips;
    private VaultHealth.Result health = VaultHealth.analyze(java.util.List.of(), LocalDateTime.now(), 365);
    private boolean healthShown;
    private final InactivityListener inactivityListener;
    private final dev.sync.SyncService syncService;

    private Predicate<PasswordEntry> viewFilter = e -> true;

    public MainFrame(PasswordManager manager) {
        super("CipherKey");
        this.manager = manager;
        setIconImage(Icons.appIcon());
        syncService = new dev.sync.SyncService(manager);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        tableModel = new PasswordTableModel(manager.getEntries());
        table = new EntryTable(tableModel);
        detail = new DetailPanel(this);
        sidebar = new Sidebar(this::onViewSelected, this::openSettings, this::lock);
        healthView = new HealthView(this::applyQueryFromHealth, this::openEntryFromHealth);
        themeButton = Ui.iconButton(themeIcon(), "Cambiar entre Grafito y Washi", e -> toggleTheme());
        syncButton = new SyncButton(e -> openSync());

        buildLayout();
        installShortcuts();

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetail();
        });
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && table.selectedEntry() != null) edit();
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent evt) { maybePopup(evt); }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) { maybePopup(evt); }
        });

        inactivityListener = new InactivityListener(AppPrefs.autoLockMinutes() * 60, this::lock);
        Toolkit.getDefaultToolkit().addAWTEventListener(inactivityListener,
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);

        refresh(null);
        syncService.setSasPrompt(this::askSas);
        syncService.setMergePrompt(this::askMerge);
        syncService.setDataListener(() -> SwingUtilities.invokeLater(() -> refresh(null)));
        syncService.start(st -> SwingUtilities.invokeLater(() -> onSyncStatus(st)));
        setMinimumSize(new Dimension(1000, 620));
        Dimension saved = AppPrefs.windowSize();
        setSize(saved != null ? saved : new Dimension(1360, 820));
        setLocationRelativeTo(null);
        if (AppPrefs.isWindowMaximized()) setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    // ---------- construcción ----------

    private void buildLayout() {
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.setViewportBorder(null);
        tableScroll.getViewport().setBackground(UIManager.getColor("Table.background"));
        JPanel listCard = new JPanel(new BorderLayout());
        listCard.add(buildListBar(), BorderLayout.NORTH);
        listCard.add(tableScroll, BorderLayout.CENTER);
        cardPanel.add(listCard, CARD_TABLE);
        cardPanel.add(buildMessage(Art.Kind.VAULT, "00 // Bóveda vacía", "Tu bóveda está vacía",
                "Guarda aquí tus contraseñas; se cifran en tu equipo.", "Añadir la primera entrada"), CARD_EMPTY);
        cardPanel.add(buildMessage(Art.Kind.SELECT, "00 // Sin resultados", "Sin resultados",
                "Prueba con otra búsqueda o cambia de vista.", null), CARD_NO_RESULTS);
        cardPanel.add(healthView, CARD_HEALTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(buildToolbar(), BorderLayout.NORTH);
        JPanel main = new JPanel(new BorderLayout());
        main.add(cardPanel, BorderLayout.CENTER);
        main.add(detail, BorderLayout.EAST);
        center.add(main, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar, BorderLayout.WEST);
        root.add(center, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JComponent buildToolbar() {
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Buscar…   filtros: is:weak  is:2fa  cat:web  user:  url:   (Ctrl+F)");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("search"));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        searchField.putClientProperty(FlatClientProperties.STYLE, "margin: 8,10,8,10");
        searchField.getAccessibleContext().setAccessibleName("Buscar entradas");
        searchField.setToolTipText("<html>Texto libre o filtros:<br><b>is:weak</b> <b>is:reused</b> <b>is:old</b> <b>is:2fa</b> <b>is:fav</b><br>"
                + "<b>cat:</b>categoría • <b>user:</b>usuario • <b>url:</b>sitio • <b>-</b> para excluir</html>");

        JButton generator = Ui.iconButton(Icons.get("key", 18), "Generador de contraseñas",
                e -> new PasswordGeneratorDialog(this, false).setVisible(true));
        syncButton.setToolTipText("Sincronización y dispositivos cercanos (Ctrl+Mayús+S)");
        JButton more = Ui.iconButton(Icons.get("more", 18), "Más opciones", e -> {});
        more.addActionListener(e -> buildMoreMenu().show(more, 0, more.getHeight()));
        JButton add = Ui.primary("Nueva entrada", Icons.onAccent("plus", 16), e -> addEntry());
        add.setToolTipText("Ctrl+N");

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(generator);
        right.add(syncButton);
        right.add(themeButton);
        right.add(more);
        right.add(Box.createHorizontalStrut(6));
        right.add(add);

        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));
        bar.add(searchField, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** Filtros rápidos (conmutan operadores en la búsqueda), recuento y orden. */
    private JComponent buildListBar() {
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chips.setOpaque(false);
        bindChip(chipWeak, "is:weak");
        bindChip(chipReused, "is:reused");
        bindChip(chipOld, "is:old");
        bindChip(chip2fa, "is:2fa");
        bindChip(chipFav, "is:fav");
        for (FilterChip c : new FilterChip[]{chipWeak, chipReused, chipOld, chip2fa, chipFav}) chips.add(c);

        JButton sort = new JButton("Orden: " + EntryTable.Sort.NAME.label(), Icons.get("layers", 14));
        sort.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        sort.putClientProperty(FlatClientProperties.STYLE, "margin: 4,8,4,8");
        sort.getAccessibleContext().setAccessibleName("Orden de la lista");
        sort.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            ButtonGroup group = new ButtonGroup();
            for (EntryTable.Sort s : EntryTable.Sort.values()) {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(s.label(), sort.getText().endsWith(s.label()));
                item.addActionListener(ev -> {
                    table.setSort(s);
                    sort.setText("Orden: " + s.label());
                });
                group.add(item);
                menu.add(item);
            }
            menu.show(sort, 0, sort.getHeight());
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(countLabel);
        right.add(sort);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 20, 10, 12));
        bar.add(chips, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void bindChip(FilterChip chip, String token) {
        chip.addActionListener(e -> {
            if (syncingChips) return;
            searchField.setText(EntryQuery.toggleToken(searchField.getText(), token));
        });
    }

    private void syncChips() {
        syncingChips = true;
        String t = searchField.getText();
        chipWeak.setSelected(EntryQuery.hasToken(t, "is:weak"));
        chipReused.setSelected(EntryQuery.hasToken(t, "is:reused"));
        chipOld.setSelected(EntryQuery.hasToken(t, "is:old"));
        chip2fa.setSelected(EntryQuery.hasToken(t, "is:2fa"));
        chipFav.setSelected(EntryQuery.hasToken(t, "is:fav"));
        syncingChips = false;
    }

    private JPopupMenu buildMoreMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem settings = new JMenuItem("Ajustes y apariencia…", Icons.get("sliders"));
        settings.addActionListener(e -> openSettings());
        JMenuItem importItem = new JMenuItem("Importar desde CSV…", Icons.get("download"));
        importItem.addActionListener(e -> importCsv());
        JMenuItem exportItem = new JMenuItem("Exportar a CSV…", Icons.get("upload"));
        exportItem.addActionListener(e -> exportCsv());
        JMenuItem backupItem = new JMenuItem("Copia de seguridad…", Icons.get("save"));
        backupItem.addActionListener(e -> backupVault());
        JMenuItem syncItem = new JMenuItem("Sincronización y dispositivos cercanos…", Icons.get("wifi"));
        syncItem.addActionListener(e -> openSync());
        JMenuItem auditItem = new JMenuItem("Salud de la bóveda", Icons.get("shield-check"));
        auditItem.addActionListener(e -> openAudit());
        JMenuItem about = new JMenuItem("Acerca de CipherKey", Icons.get("info"));
        about.addActionListener(e -> new AboutDialog(this).setVisible(true));
        JMenuItem lockItem = new JMenuItem("Bloquear bóveda", Icons.get("lock"));
        lockItem.addActionListener(e -> lock());
        JMenuItem exit = new JMenuItem("Salir", Icons.get("x"));
        exit.addActionListener(e -> shutdown());
        menu.add(settings);
        menu.add(auditItem);
        menu.add(syncItem);
        menu.addSeparator();
        menu.add(importItem);
        menu.add(exportItem);
        menu.add(backupItem);
        menu.addSeparator();
        menu.add(about);
        menu.addSeparator();
        menu.add(lockItem);
        menu.add(exit);
        return menu;
    }

    private JComponent buildMessage(Art.Kind kind, String caption, String title, String text, String buttonText) {
        JPanel p = new JPanel(new GridBagLayout());
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JComponent art = Art.panel(kind, caption);
        JLabel t = new JLabel(title);
        t.setFont(Fonts.bold(20));
        JLabel d = Ui.muted(text);
        for (JComponent c : new JComponent[]{art, t, d}) c.setAlignmentX(CENTER_ALIGNMENT);
        box.add(art);
        box.add(Box.createVerticalStrut(6));
        box.add(t);
        box.add(Box.createVerticalStrut(6));
        box.add(d);
        if (buttonText != null) {
            JButton b = Ui.primary(buttonText, Icons.onAccent("plus", 16), e -> addEntry());
            b.setAlignmentX(CENTER_ALIGNMENT);
            box.add(Box.createVerticalStrut(18));
            box.add(b);
        }
        p.add(box);
        return p;
    }

    private void installShortcuts() {
        JRootPane root = getRootPane();
        bind(root, "ctrl N", "new", this::addEntry);
        bind(root, "ctrl F", "search", () -> {
            searchField.requestFocusInWindow();
            searchField.selectAll();
        });
        bind(root, "ctrl L", "lock", this::lock);
        bind(root, "ctrl shift S", "sync", this::openSync);
        bind(root, "ctrl B", "copyPassword", this::copyPassword);
        bind(root, "ctrl U", "copyUser", this::copyUsername);
        bind(root, "ctrl E", "edit", this::edit);
        bind(root, "ctrl H", "health", this::openAudit);

        InputMap tableKeys = table.getInputMap(JComponent.WHEN_FOCUSED);
        tableKeys.put(KeyStroke.getKeyStroke("DELETE"), "deleteEntry");
        tableKeys.put(KeyStroke.getKeyStroke("ENTER"), "editEntry");
        table.getActionMap().put("deleteEntry", action(this::delete));
        table.getActionMap().put("editEntry", action(this::edit));

        searchField.registerKeyboardAction(e -> {
            if (searchField.getText().isEmpty()) table.requestFocusInWindow();
            else searchField.setText("");
        }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_FOCUSED);
        searchField.registerKeyboardAction(e -> table.requestFocusInWindow(),
                KeyStroke.getKeyStroke("DOWN"), JComponent.WHEN_FOCUSED);
    }

    private static void bind(JRootPane root, String stroke, String name, Runnable r) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), name);
        root.getActionMap().put(name, action(r));
    }

    private static Action action(Runnable r) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        };
    }

    private void maybePopup(java.awt.event.MouseEvent evt) {
        if (!evt.isPopupTrigger()) return;
        int row = table.rowAtPoint(evt.getPoint());
        if (row < 0) return;
        table.setRowSelectionInterval(row, row);
        PasswordEntry e = table.selectedEntry();

        JPopupMenu menu = new JPopupMenu();
        add(menu, "Copiar usuario", "copy", this::copyUsername);
        add(menu, "Copiar contraseña", "copy", this::copyPassword);
        if (e.getUrl() != null && !e.getUrl().isBlank()) add(menu, "Abrir sitio web", "external-link", this::openUrl);
        menu.addSeparator();
        add(menu, "Editar", "edit", this::edit);
        add(menu, e.isFavorite() ? "Quitar de favoritas" : "Marcar como favorita", "star", this::toggleFavorite);
        menu.addSeparator();
        add(menu, "Eliminar", "trash", this::delete);
        menu.show(table, evt.getX(), evt.getY());
    }

    private static void add(JPopupMenu menu, String text, String icon, Runnable r) {
        JMenuItem item = new JMenuItem(text, Icons.get(icon));
        item.addActionListener(e -> r.run());
        menu.add(item);
    }

    // ---------- filtro y estado ----------

    private void onViewSelected(Sidebar.View view) {
        healthShown = Sidebar.KEY_HEALTH.equals(view.key());
        viewFilter = view.filter();
        if (healthShown) healthView.update(health, true);
        applyFilter();
    }

    private void applyFilter() {
        EntryQuery query = EntryQuery.parse(searchField.getText());
        table.setFilter(entry -> viewFilter.test(entry) && query.matches(entry, health.of(entry)));
        syncChips();
        int total = manager.getEntries().size();
        countLabel.setText(table.getRowCount() == total ? total + (total == 1 ? " ENTRADA" : " ENTRADAS")
                : table.getRowCount() + " DE " + total);
        updateCard();
        showDetail();
    }

    private void showDetail() {
        PasswordEntry sel = table.selectedEntry();
        detail.show(sel, sel == null ? null : health.of(sel));
    }

    private void updateCard() {
        if (healthShown) cards.show(cardPanel, CARD_HEALTH);
        else if (manager.getEntries().isEmpty()) cards.show(cardPanel, CARD_EMPTY);
        else if (table.getRowCount() == 0) cards.show(cardPanel, CARD_NO_RESULTS);
        else cards.show(cardPanel, CARD_TABLE);
    }

    /** Tras cambiar los datos: actualiza tabla, salud, barra lateral y selección. */
    private void refresh(Integer selectId) {
        tableModel.setEntries(manager.getEntries());
        health = VaultHealth.analyze(manager.getEntries(), LocalDateTime.now(), PasswordAudit.DEFAULT_OLD_DAYS);
        table.setHealth(health);
        table.resort();
        chipWeak.setCount(health.weak() + health.empty());
        chipReused.setCount(health.reused());
        chipOld.setCount(health.old());
        sidebar.refresh(manager.getEntries(), health);
        if (healthShown) healthView.update(health, false);
        updateCard();
        if (selectId != null) table.selectById(selectId);
        showDetail();
    }

    private Icon themeIcon() {
        return Icons.get(ThemeManager.isDark() ? "sun" : "moon", 18);
    }

    private void toggleTheme() {
        ThemeManager.toggle();
        themeButton.setIcon(themeIcon());
    }

    // ---------- acciones ----------

    private void addEntry() {
        EntryDialog dialog = new EntryDialog(this, null, sidebar.categories());
        if (dialog.showDialog()) {
            PasswordEntry created = dialog.getEntry();
            manager.addEntry(created);
            if (save()) Toast.show(this, "Entrada añadida");
            clearViewFilters();
            refresh(created.getId());
        }
    }

    /** Evita que una entrada recién creada quede oculta por la búsqueda activa. */
    private void clearViewFilters() {
        if (!searchField.getText().isEmpty()) searchField.setText("");
    }

    @Override
    public void edit() {
        PasswordEntry entry = table.selectedEntry();
        if (entry == null) return;
        EntryDialog dialog = new EntryDialog(this, entry, sidebar.categories());
        if (dialog.showDialog()) {
            PasswordEntry updated = dialog.getEntry();
            manager.updateEntry(updated);
            if (save()) Toast.show(this, "Cambios guardados");
            refresh(updated.getId());
        }
    }

    @Override
    public void delete() {
        PasswordEntry entry = table.selectedEntry();
        if (entry == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Eliminar «" + entry.getTitle() + "»? Esta acción no se puede deshacer.",
                "Eliminar entrada", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            manager.deleteEntry(entry.getId());
            if (save()) Toast.show(this, "Entrada eliminada");
            refresh(null);
        }
    }

    @Override
    public void toggleFavorite() {
        PasswordEntry entry = table.selectedEntry();
        if (entry == null) return;
        entry.setFavorite(!entry.isFavorite());
        save();
        refresh(entry.getId());
    }

    @Override
    public void copyUsername() {
        PasswordEntry e = table.selectedEntry();
        if (e == null || e.getUsername() == null || e.getUsername().isEmpty()) return;
        SecureClipboard.copyToClipboard(e.getUsername());
        Toast.show(this, "Usuario copiado • se borrará en 30 s");
    }

    @Override
    public void copyPassword() {
        PasswordEntry e = table.selectedEntry();
        if (e == null || e.getPassword() == null || e.getPassword().isEmpty()) return;
        SecureClipboard.copyToClipboard(e.getPassword());
        Toast.show(this, "Contraseña copiada • se borrará en 30 s");
    }

    @Override
    public void openUrl() {
        PasswordEntry e = table.selectedEntry();
        if (e == null || e.getUrl() == null || e.getUrl().isBlank()) return;
        String url = e.getUrl().trim();
        if (!url.contains("://")) url = "https://" + url;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            // Solo web: nunca ejecutar esquemas arbitrarios (file:, javascript:…) guardados en una entrada.
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                Toast.show(this, "Solo se pueden abrir enlaces http(s)");
                return;
            }
            Desktop.getDesktop().browse(uri);
        } catch (Exception ex) {
            Toast.show(this, "No se pudo abrir el enlace");
        }
    }

    @Override
    public void copyTotp() {
        PasswordEntry e = table.selectedEntry();
        if (e == null || e.getTotpSecret() == null) return;
        try {
            SecureClipboard.copyToClipboard(Totp.parse(e.getTotpSecret()).code(Instant.now()));
            Toast.show(this, "Código 2FA copiado • se borrará en 30 s");
        } catch (IllegalArgumentException ex) {
            Toast.show(this, "La clave 2FA no es válida");
        }
    }

    @Override
    public void showHistory() {
        PasswordEntry e = table.selectedEntry();
        if (e != null) new HistoryDialog(this, e).setVisible(true);
    }

    /** «Salud de la bóveda» sustituye a la antigua ventana de auditoría: es una vista más de la barra lateral. */
    private void openAudit() {
        sidebar.select(Sidebar.KEY_HEALTH);
    }

    private void applyQueryFromHealth(String query) {
        sidebar.selectAll();
        searchField.setText(query);
        table.requestFocusInWindow();
        if (table.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
    }

    private void openEntryFromHealth(PasswordEntry entry) {
        searchField.setText("");
        sidebar.selectAll();
        table.selectById(entry.getId());
        table.requestFocusInWindow();
    }

    private void importCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Importar contraseñas desde CSV");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos CSV", "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            EntryCsv.Imported imported = EntryCsv.read(Files.readString(fc.getSelectedFile().toPath()));
            int added = manager.importEntries(imported.entries());
            if (added > 0 && !save()) return;
            clearViewFilters();
            refresh(null);
            int duplicates = imported.entries().size() - added;
            JOptionPane.showMessageDialog(this,
                    "<html><b>Importadas: " + added + "</b><br>Duplicadas (ya existían): " + duplicates
                            + "<br>Omitidas (no son inicios de sesión): " + imported.skipped()
                            + "<br><br>El CSV contiene tus contraseñas <b>sin cifrar</b>: bórralo cuando termines.</html>",
                    "Importación terminada", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException | IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo importar: " + ex.getMessage(), "Importar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportCsv() {
        int ok = JOptionPane.showConfirmDialog(this,
                "<html>El archivo exportado contendrá todas tus contraseñas <b>en texto plano</b>.<br>"
                        + "Guárdalo en un lugar seguro y bórralo cuando ya no lo necesites.<br><br>¿Continuar?</html>",
                "Exportar a CSV", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        Path target = chooseSaveTarget("Exportar a CSV", "cipherkey-export.csv", "csv", "Archivos CSV");
        if (target == null) return;
        try {
            EntryCsv.writeFile(target, manager.getEntries());
            Toast.show(this, "Exportadas " + manager.getEntries().size() + " entradas");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo exportar: " + ex.getMessage(), "Exportar", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** La bóveda ya está cifrada y guardada tras cada cambio, así que basta con copiar el archivo. */
    private void backupVault() {
        Path source = manager.getVaultFile();
        String stem = source.getFileName().toString().replaceFirst("\\.ckey$", "");
        String name = stem + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".ckey";
        Path target = chooseSaveTarget("Copia de seguridad", name, "ckey", "Bóvedas CipherKey");
        if (target == null) return;
        try {
            if (Files.isSameFile(source, target)) {
                JOptionPane.showMessageDialog(this, "Elige un destino distinto de la bóveda original.");
                return;
            }
        } catch (IOException ignored) {
            // el destino aún no existe: no es el mismo archivo
        }
        try {
            manager.exportEncryptedCopy(target);
            Toast.show(this, "Copia guardada (cifrada con tu clave, sin la identidad de sincronización)");
        } catch (VaultException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo copiar: " + ex.getMessage(), "Copia de seguridad", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Selector de archivo para guardar, con aviso si el destino ya existe. Devuelve {@code null} si se cancela. */
    private Path chooseSaveTarget(String title, String defaultName, String extension, String description) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setSelectedFile(new java.io.File(defaultName));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(description, extension));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        java.io.File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith("." + extension)) f = new java.io.File(f.getAbsolutePath() + "." + extension);
        if (f.exists() && JOptionPane.showConfirmDialog(this, "«" + f.getName() + "» ya existe. ¿Sustituirlo?",
                title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return null;
        }
        return f.toPath();
    }

    private void saveWindowState() {
        boolean maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        AppPrefs.setWindowMaximized(maximized);
        if (!maximized) AppPrefs.setWindowSize(getWidth(), getHeight());
    }

    private void openSettings() {
        SettingsDialog dlg = new SettingsDialog(this, manager);
        dlg.setVisible(true);
        inactivityListener.setTimeoutSeconds(AppPrefs.autoLockMinutes() * 60);
        if (dlg.isPasswordChanged()) Toast.show(this, "Clave maestra cambiada");
    }

    /** Cada cambio se guarda en el acto (cifrar es barato: la KDF solo corre al abrir). */
    private boolean save() {
        try {
            manager.saveData();
            syncService.syncSoon();
            return true;
        } catch (VaultException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /** Muestra el código de seguridad de la vinculación por proximidad y espera la decisión (se llama desde un hilo de red). */
    private boolean askSas(String peer, String sas, boolean incoming) throws Exception {
        boolean[] ok = {false};
        SwingUtilities.invokeAndWait(() -> {
            Toolkit.getDefaultToolkit().beep();
            ok[0] = new SasDialog(this, peer, sas, incoming).ask();
        });
        return ok[0];
    }

    /** Resumen de la primera fusión con otro dispositivo: el usuario decide; antes de fusionar se hace una copia de seguridad. */
    private boolean askMerge(dev.cipherkey.core.p2p.PeerSync.Summary x) throws Exception {
        boolean[] ok = {false};
        SwingUtilities.invokeAndWait(() -> {
            String msg = "<html><b>Primera sincronización con «" + Ui.escapeHtml(x.peerName()) + "»</b><br><br>"
                    + "• " + x.mineOnly() + " entradas nuevas de este dispositivo (se enviarán al otro)<br>"
                    + "• " + x.theirsOnly() + " entradas nuevas del otro dispositivo (se añadirán aquí)<br>"
                    + "• " + x.differing() + " con el mismo identificador pero contenido distinto (se conservan las dos versiones como conflicto)<br><br>"
                    + "Se hará una copia de seguridad de tu bóveda antes de fusionar. "
                    + "No se borra ni se sustituye ninguna entrada.</html>";
            Object[] opts = {"Fusionar", "Cancelar"};
            ok[0] = JOptionPane.showOptionDialog(this, msg, "Fusionar bóvedas", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, opts, opts[1]) == 0;
        });
        return ok[0];
    }

    private void openSync() {
        new SyncDialog(this, syncService, () -> refresh(null)).setVisible(true);
    }

    /** Tras cada sincronización: si cambiaron los datos, se refresca la lista conservando la selección. */
    private void onSyncStatus(dev.sync.SyncService.Status st) {
        syncButton.setState(st);
        if (st.running()) return;
        Integer sel = table.selectedEntry() == null ? null : table.selectedEntry().getId();
        refresh(sel);
    }

    private void lock() {
        syncService.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(inactivityListener);
        saveWindowState();
        manager.lock();
        dispose();
        App.showLogin();
    }

    private void shutdown() {
        syncService.stop();
        saveWindowState();
        manager.lock();
        System.exit(0);
    }
}
