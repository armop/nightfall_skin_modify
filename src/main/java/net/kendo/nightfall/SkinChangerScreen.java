package net.kendo.nightfall;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout (fixed, ไม่ซ้อนทับกัน):
 *
 *  ┌─[title bar 24px]──────────────────────────────────┐
 *  │  [History 22%]  │  [Viewer 28%]  │  [Controls 50%]│
 *  └────────────────────────────────────────────────────┘
 *
 * Controls column แบ่งจากบนลงล่าง:
 *   pad | DropZone(30% ctrlH) | pad | status | pad | urlLabel | urlBox | btnRow | pad | buttons... | pad
 */
@OnlyIn(Dist.CLIENT)
public class SkinChangerScreen extends Screen {

    // ── สี ──
    private static final int BG      = 0xCC070710;
    private static final int BORDER  = 0xFF2D2D55;
    private static final int ACCENT  = 0xFF4455DD;
    private static final int TITLE   = 0xFFCCCCFF;
    private static final int MUTED   = 0xFF7777AA;
    private static final int SUCCESS = 0xFF44EE88;
    private static final int ERROR   = 0xFFFF5555;
    private static final int WARN    = 0xFFFFDD44;
    private static final int WHITE   = 0xFFEEEEEE;

    // ── layout vars (คำนวณใน calcLayout) ──
    private int pad;
    // columns
    private int hX, hY, hW, hH;   // history
    private int vX, vY, vW, vH;   // viewer
    private int cX, cY, cW, cH;   // controls
    // inside controls
    private int dzX, dzY, dzW, dzH; // drop zone
    private int urlY;               // Y ของ url label/box
    private int btnStartY;          // Y เริ่มต้นของปุ่มกลุ่ม
    private int controlsScroll = 0;
    private int maxControlsScroll = 0;
    // ── state ──
    private float rotY = 20f, rotX = -8f;
    private boolean draggingModel;
    private double lastMX, lastMY;
    private File   pendingFile = null;
    private String status    = "วางไฟล์สกิน PNG ลงในกรอบ หรือใส่ URL";
    private int    statusCol = MUTED;
    private boolean isSlim;
    private int    histScroll = 0, maxHistScroll = 0;
    private boolean loadingUrl = false;
    private List<String> droppedPaths = new ArrayList<>();
    private boolean draggingFile = false;
    private EditBox urlBox;

    public SkinChangerScreen() { super(Component.literal("Skin Changer")); }

    // ══════════════════════════════ LAYOUT ════════════════════════════════════

    private void calcLayout() {

        pad = Math.max(5, this.width / 120);

        int TOP = 25;
        int BOT = 4;

        int availH = this.height - TOP - BOT;

        // columns
        hX = pad;
        hY = TOP;
        hW = (int)(this.width * 0.22f) - pad;
        hH = availH;

        vX = hX + hW + pad;
        vY = TOP;
        vW = (int)(this.width * 0.28f) - pad;
        vH = availH;

        cX = vX + vW + pad;
        cY = TOP;
        cW = this.width - cX - pad;
        cH = availH;

        // dropzone
        dzX = cX;
        dzY = cY + pad;

        dzW = cW;
        dzH = Math.max(55, (int)(cH * 0.28f));

        // dynamic positions
        int y = cY + pad;

        y += dzH + pad;
        y += 24;

        urlY = y;

        y += 13;
        y += 20;
        y += pad;
        y += 20;
        y += pad * 2;

        btnStartY = y;

        // content height
        int contentHeight =
                dzH +
                        pad +
                        24 +
                        13 +
                        20 +
                        pad +
                        20 +
                        pad * 2 +
                        18 +
                        ((20 + pad) * 6) +
                        pad * 2;

        maxControlsScroll = Math.max(
                0,
                contentHeight - (cH - pad * 2)
        );

        controlsScroll = Math.min(
                controlsScroll,
                maxControlsScroll
        );
    }

    // ══════════════════════════════ INIT ══════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        calcLayout();
        isSlim = ModelPreferenceManager.isSlimPreference();
        refreshScroll();

        // URL box
        urlBox = new EditBox(font, cX + pad, urlY + 13, cW - pad * 2, 18, Component.literal("url"));
        urlBox.setHint(Component.literal("§8https://example.com/skin.png"));
        urlBox.setMaxLength(500);
        addWidget(urlBox);

        // URL buttons (half width each)
        int half = (cW - pad * 3) / 2;
        int bRowY = urlY + 13 + 20 + pad;
        addRW(btn("§aโหลด URL",  cX + pad,           bRowY, half, 18, b -> loadFromUrl()));
        addRW(btn("§7ล้าง",      cX + pad * 2 + half, bRowY, half, 18, b -> { urlBox.setValue(""); setStatus("ล้าง URL แล้ว", MUTED); }));

        // action buttons
        int fw = cW - pad * 2;
        int bH = 20;
        int bY = btnStartY + 18 + pad;
        addRW(btn(isSlim ? "Model: §bSlim" : "Model: §fWide", cX + pad, bY, fw, bH, this::toggleModel)); bY += bH + pad;
        addRW(btn("§aเปลี่ยนสกิน",  cX + pad, bY, fw, bH, b -> doApply()));   bY += bH + pad;
        addRW(btn("§7เปิดไฟล์...",  cX + pad, bY, fw, bH, b -> browseFile())); bY += bH + pad;
        addRW(btn("§cรีเซ็ตสกิน",  cX + pad, bY, fw, bH, b -> doReset()));    bY += bH + pad * 2;
        addRW(btn("§8รีเซ็ตมุมมอง", cX + pad, bY, fw, bH, b -> { rotY = 20f; rotX = -8f; })); bY += bH + pad;
        addRW(btn("§4ปิด",          cX + pad, bY, fw, bH, b -> onClose()));
    }

    private void addRW(Button b) { addRenderableWidget(b); }
    private Button btn(String lbl, int x, int y, int w, int h, Button.OnPress fn) {
        return Button.builder(Component.literal(lbl), fn).bounds(x, y, w, h).build();
    }
    private int itemH() { return Math.max(50, (int)(hH * 0.14f)); }

    // ══════════════════════════════ RENDER ════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0x99050510);

        // title bar
        g.fill(0, 0, width, 25, 0xEE080818);
        g.fill(0, 24, width, 25, ACCENT);
        g.drawCenteredString(font, "§lSkin §7Changer", width / 2, 7, TITLE);

        panel(g, hX, hY, hW, hH);
        panel(g, vX, vY, vW, vH);
        panel(g, cX, cY, cW, cH);

        renderHistory(g, mx, my);
        renderViewer(g, dt);
        renderControls(g);



        super.render(g, mx, my, dt);



        urlBox.render(g, mx, my, dt);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x,       y,     x+w,   y+1,   BORDER);
        g.fill(x,       y+h-1, x+w,   y+h,   BORDER);
        g.fill(x,       y,     x+1,   y+h,   BORDER);
        g.fill(x+w-1,   y,     x+w,   y+h,   BORDER);
        g.fill(x+1,     y+1,   x+w-1, y+3,   ACCENT);  // accent top bar
    }

    // ── HISTORY ───────────────────────────────────────────────────────────────

    private void renderHistory(GuiGraphics g, int mx, int my) {
        g.drawString(font, "§fRecent Skins", hX + pad, hY + 5, TITLE);
        g.fill(hX+1, hY+16, hX+hW-1, hY+17, BORDER);

        int ih = itemH(), startY = hY + 18, clipH = hH - 18;
        List<SkinHistory.SkinEntry> hist = SkinHistory.getHistory();

        for (int i = 0; i < hist.size(); i++) {
            SkinHistory.SkinEntry e = hist.get(i);
            int iy = startY + i * ih - histScroll;
            if (iy + ih <= startY || iy >= startY + clipH) continue;

            boolean hov = mx >= hX+1 && mx <= hX+hW-1 && my >= iy && my < iy+ih;
            g.fill(hX+1, iy, hX+hW-1, iy+ih-1, hov ? 0x50AAAAFF : 0x20FFFFFF);

            int th = ih - 10, tx = hX+pad, ty = iy+5;
            ResourceLocation tid = getOrLoadThumb(e);
            if (tid != null) { try { g.blit(tid, tx, ty, th, th, 8f, 8f, 8, 8, 64, 64); } catch (Exception ignored) {} }
            else g.fill(tx, ty, tx+th, ty+th, 0xFF1A1A2A);

            int tx2 = tx + th + pad, maxTW = hW - th - pad*3;
            String name = e.getDisplayName();
            if (font.width(name) > maxTW) name = name.substring(0, Math.max(1, maxTW/6))+"…";
            g.drawString(font, "§f"+name,                                tx2, iy+4,  WHITE);
            g.drawString(font, "§8"+(e.isSlim()?"Slim":"Wide"),          tx2, iy+14, MUTED);
            g.drawString(font, "§8"+e.getTimeAgo(),                      tx2, iy+24, MUTED);
        }

        if (maxHistScroll > 0 && !hist.isEmpty()) {
            int sbH = Math.max(14, clipH*clipH/Math.max(1, hist.size()*ih));
            int sbY = startY + (int)((float)histScroll/maxHistScroll*(clipH-sbH));
            g.fill(hX+hW-4, sbY, hX+hW-2, sbY+sbH, 0xFF5566CC);
        }
    }

    private ResourceLocation getOrLoadThumb(SkinHistory.SkinEntry e) {
        ResourceLocation t = e.getTextureId();
        if (t != null && minecraft.getTextureManager().getTexture(t) != null) return t;
        if (!e.getFile().exists()) return null;
        try {
            BufferedImage img = ImageIO.read(e.getFile());
            if (img == null) return null;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NightfallSkin.MOD_ID,
                    "ht_"+Math.abs(e.getFile().getName().hashCode()));
            minecraft.getTextureManager().register(id, new DynamicTexture(toNative(img)));
            e.setTextureId(id);
            return id;
        } catch (Exception ex) { return null; }
    }

    // ── VIEWER ────────────────────────────────────────────────────────────────

    private void renderViewer(GuiGraphics g, float dt) {
        g.drawCenteredString(font, "§fSkin Preview", vX+vW/2, vY+5, TITLE);
        g.fill(vX+1, vY+16, vX+vW-1, vY+17, BORDER);
        g.drawCenteredString(font, "§8ลากซ้าย-ขวาเพื่อหมุน", vX+vW/2, vY+vH-12, MUTED);

        if (!draggingModel) rotY += dt * 0.4f;

        if (minecraft.player != null) {
            // scale: ให้โมเดลสูงไม่เกิน 80% ของพื้นที่ viewer (ลบ header/footer)
            int innerH = vH - 32;  // หัก header 18 + footer 14
            float scale = innerH * 0.38f;  // ~player height 1.8 blocks → fit

            // cx กึ่งกลาง, cy = บน + 18 (header) + innerH*0.85 (เท้าอยู่ที่ 85%)
            int cx = vX + vW/2;
            int cy = vY + 18 + (int)(innerH * 0.85f);
            renderPlayer(g, cx, cy, scale);
        }
    }

    private void renderPlayer(GuiGraphics g, int cx, int cy, float scale) {
        PoseStack m = g.pose();
        m.pushPose();
        m.translate(cx, cy, 1050f);
        m.scale(1f, -1f, 1f);
        Quaternionf q = new Quaternionf();
        q.rotateY((float)Math.toRadians(-rotY));
        q.rotateX((float)Math.toRadians(-rotX));
        m.mulPose(q);
        m.scale(scale, scale, scale);

        EntityRenderDispatcher d = minecraft.getEntityRenderDispatcher();
        d.setRenderShadow(false);
        MultiBufferSource.BufferSource buf = minecraft.renderBuffers().bufferSource();
        try {
            d.render(minecraft.player, 0, 0, 0, 0f, 1f, m, buf, LightTexture.FULL_BRIGHT);
            buf.endBatch();
        } catch (Exception ignored) {}
        d.setRenderShadow(true);
        m.popPose();
    }

    // ── CONTROLS ──────────────────────────────────────────────────────────────
    private void renderControls(GuiGraphics g) {

        int y = cY + pad - controlsScroll;

        // ── Drop Zone ──
        dzY = y;

        int dBorder = draggingFile ? SUCCESS : BORDER;
        int dBg     = draggingFile ? 0x2000FF44 : 0x10AAAAFF;

        g.fill(dzX+1, dzY+1, dzX+dzW-1, dzY+dzH-1, dBg);

        g.fill(dzX, dzY, dzX+dzW, dzY+1, dBorder);
        g.fill(dzX, dzY+dzH-1, dzX+dzW, dzY+dzH, dBorder);
        g.fill(dzX, dzY, dzX+1, dzY+dzH, dBorder);
        g.fill(dzX+dzW-1, dzY, dzX+dzW, dzY+dzH, dBorder);

        int mid = dzY + dzH / 2;

        g.drawCenteredString(font,
                "§7⬇ วางไฟล์สกิน .png",
                dzX + dzW / 2,
                mid - 10,
                MUTED);

        if (pendingFile != null) {
            String n = pendingFile.getName();

            if (font.width(n) > dzW - 10)
                n = n.substring(0, 20) + "…";

            g.drawCenteredString(font,
                    "§a" + n,
                    dzX + dzW / 2,
                    mid + 4,
                    SUCCESS);
        } else {
            g.drawCenteredString(font,
                    "§8(หรือใส่ URL ด้านล่าง)",
                    dzX + dzW / 2,
                    mid + 4,
                    0x55AAAAAA);
        }

        y += dzH + pad;

        // ── Status ──
        List<String> lines = wrap(status, cW - pad * 2);

        for (int i = 0; i < Math.min(lines.size(), 2); i++) {
            g.drawCenteredString(font,
                    lines.get(i),
                    cX + cW / 2,
                    y + i * 10,
                    statusCol);
        }

        y += 24;

        // ── URL ──
        g.drawString(font,
                "§8Skin URL:",
                cX + pad,
                y,
                MUTED);

        y += 13;

        urlBox.setX(cX + pad);
        urlBox.setY(y);
        urlBox.setWidth(cW - pad * 2);

        y += 20 + pad;

        // buttons url
        int half = (cW - pad * 3) / 2;

        for (var w : this.renderables) {
            if (w instanceof Button b) {

                String txt = b.getMessage().getString();

                if (txt.contains("โหลด URL")) {
                    b.setX(cX + pad);
                    b.setY(y);
                    b.setWidth(half);
                }

                if (txt.equals("§7ล้าง")) {
                    b.setX(cX + pad * 2 + half);
                    b.setY(y);
                    b.setWidth(half);
                }
            }
        }

        y += 20 + pad * 2;

        // label
        g.drawString(font,
                "§8─── การตั้งค่า ───",
                cX + pad,
                y,
                MUTED);

        y += 18 + pad;

        // buttons
        for (var w : this.renderables) {
            if (w instanceof Button b) {

                String txt = b.getMessage().getString();

                if (!txt.contains("โหลด URL")
                        && !txt.equals("§7ล้าง")) {

                    b.setX(cX + pad);
                    b.setY(y);
                    b.setWidth(cW - pad * 2);

                    y += 20 + pad;
                }
            }
        }

        // scrollbar
        if (maxControlsScroll > 0) {

            int trackY = cY + pad;
            int trackH = cH - pad * 2;

            if (trackH <= 0) return;

            int denom = Math.max(1, trackH + maxControlsScroll);

            int barH = Math.max(
                    18,
                    (trackH * trackH) / denom
            );

            float scrollProgress =
                    maxControlsScroll <= 0
                            ? 0F
                            : (float) controlsScroll / (float) maxControlsScroll;

            int barY = trackY + (int)(
                    scrollProgress * (trackH - barH)
            );

            g.fill(
                    cX + cW - 4,
                    barY,
                    cX + cW - 2,
                    barY + barH,
                    ACCENT
            );
        }
    }

    // ══════════════════════════════ ACTIONS ═══════════════════════════════════

    private void toggleModel(Button b) {
        isSlim = !isSlim;
        ModelPreferenceManager.setSlimPreference(isSlim);
        b.setMessage(Component.literal(isSlim ? "Model: §bSlim" : "Model: §fWide"));
        if (SkinManager.getCurrentCustomSkin() != null) reapply();
        else setStatus("บันทึกค่า model: "+(isSlim?"Slim":"Wide"), WARN);
    }

    private void reapply() {
        try {
            SkinManager.SkinData d = SkinManager.getSkinData(minecraft.player.getUUID());
            if (d != null && d.imageData != null) {
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(d.imageData));
                SkinManager.applySkin(minecraft, img, isSlim);
                setStatus("เปลี่ยน model เป็น "+(isSlim?"Slim":"Wide")+"!", SUCCESS);
            }
        } catch (Exception e) { setStatus("เปลี่ยน model ไม่สำเร็จ", ERROR); }
    }

    private void doApply() {
        if (pendingFile != null && pendingFile.exists()) applySkin(pendingFile);
        else if (urlBox != null && !urlBox.getValue().isEmpty() && !loadingUrl) loadFromUrl();
        else setStatus("เลือกไฟล์หรือใส่ URL ก่อน", WARN);
    }

    private void doReset() {
        SkinManager.resetSkin(minecraft); pendingFile = null;
        setStatus("รีเซ็ตสกินแล้ว", MUTED);
    }

    private void applySkin(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) { setStatus("อ่านรูปไม่ได้!", ERROR); return; }
            ResourceLocation tid = SkinManager.applySkin(minecraft, img, isSlim);
            SkinHistory.addSkin(f, tid, isSlim);
            playSound();
            setStatus("เปลี่ยนสกินสำเร็จ! ("+(isSlim?"Slim":"Classic")+")", SUCCESS);
            refreshScroll();
        } catch (Exception e) { setStatus("Error: "+e.getMessage(), ERROR); }
    }

    private void loadFromUrl() {
        String url = urlBox.getValue().trim();
        if (url.isEmpty()) { setStatus("กรุณาใส่ URL", WARN); return; }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            setStatus("URL ต้องขึ้นต้นด้วย http(s)://", ERROR); return;
        }
        loadingUrl = true; pendingFile = null; setStatus("กำลังดาวน์โหลด...", WARN);
        SkinManager.applySkinFromUrl(minecraft, url, isSlim)
            .thenAccept(tid -> minecraft.execute(() -> {
                loadingUrl = false;
                setStatus(tid != null ? "โหลดสกินจาก URL สำเร็จ!" : "โหลด URL ไม่สำเร็จ",
                          tid != null ? SUCCESS : ERROR);
                if (tid != null) { playSound(); refreshScroll(); }
                rebuildWidgets();
            }))
            .exceptionally(e -> { minecraft.execute(() -> { loadingUrl = false; setStatus("Error: "+shorten(e.getMessage(),50), ERROR); }); return null; });
    }

    private void browseFile() {
        new Thread(() -> {
            try {
                String home = System.getProperty("user.home");
                File dl = new File(home, "Downloads");
                String p = TinyFileDialogs.tinyfd_openFileDialog("เลือกสกิน PNG", dl.exists() ? dl.getAbsolutePath() : home, null, null, false);
                minecraft.execute(() -> {
                    if (p != null) { File f=new File(p); if (validSkin(f)) { pendingFile=f; setStatus("เลือกไฟล์แล้ว กด 'เปลี่ยนสกิน'", SUCCESS); } else setStatus("ไฟล์ไม่ถูกต้อง (PNG 64–512px)", ERROR); }
                    else setStatus("ยกเลิก", MUTED);
                });
            } catch (Exception e) { minecraft.execute(() -> setStatus("เปิด browser ไม่ได้", ERROR)); }
        }).start();
        setStatus("กำลังเปิด file browser...", WARN);
    }

    // ══════════════════════════════ HELPERS ═══════════════════════════════════

    private boolean validSkin(File f) {
        try {
            if (!f.getName().toLowerCase().endsWith(".png")) return false;
            BufferedImage i = ImageIO.read(f); if (i==null) return false;
            return i.getWidth()==i.getHeight() && i.getWidth()>=64 && i.getWidth()<=512 && i.getWidth()%64==0;
        } catch (Exception e) { return false; }
    }
    private void setStatus(String m, int c) { status=m; statusCol=c; }
    private void refreshScroll() {
        maxHistScroll = Math.max(0, SkinHistory.getHistory().size()*itemH() - (hH-18));
    }
    private void playSound() {
        if (minecraft.player!=null && minecraft.level!=null) {
            net.minecraft.sounds.SoundEvent s = Math.random()<0.5 ? ModSounds.SKIN_CHANGE_1.get() : ModSounds.SKIN_CHANGE_2.get();
            minecraft.level.playSound(minecraft.player, minecraft.player.blockPosition(), s, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1f);
        }
    }
    private List<String> wrap(String t, int maxW) {
        List<String> out=new ArrayList<>(); StringBuilder cur=new StringBuilder();
        for (String w : t.split(" ")) { String test=cur.length()==0?w:cur+" "+w;
            if (font.width(test)<=maxW) { if(cur.length()>0) cur.append(' '); cur.append(w); }
            else { if(cur.length()>0) out.add(cur.toString()); cur=new StringBuilder(w); } }
        if (cur.length()>0) out.add(cur.toString()); return out;
    }
    private String shorten(String s, int max) { return s==null?"":s.length()>max?s.substring(0,max)+"…":s; }
    private NativeImage toNative(BufferedImage src) {
        int w=src.getWidth(), h=src.getHeight(); NativeImage ni=new NativeImage(w,h,true);
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) { int rgb=src.getRGB(x,y);
            int a=(rgb>>24)&0xFF,r=(rgb>>16)&0xFF,gg=(rgb>>8)&0xFF,b=rgb&0xFF;
            ni.setPixelRGBA(x,y,(a<<24)|(b<<16)|(gg<<8)|r); }
        return ni;
    }

    // ══════════════════════════════ INPUT ═════════════════════════════════════

    @Override
    public boolean mouseScrolled(double mx, double my, double amt) {

        // history
        if (mx >= hX && mx <= hX + hW) {

            histScroll = Math.max(
                    0,
                    Math.min(
                            maxHistScroll,
                            histScroll - (int)(amt * 15)
                    )
            );

            return true;
        }

        // controls
        if (mx >= cX && mx <= cX + cW) {

            controlsScroll = Math.max(
                    0,
                    Math.min(
                            maxControlsScroll,
                            controlsScroll - (int)(amt * 15)
                    )
            );

            return true;
        }

        return super.mouseScrolled(mx, my, amt);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (btn==0 && mx>=vX && mx<=vX+vW && my>=vY && my<=vY+vH) { draggingModel=true; lastMX=mx; lastMY=my; return true; }
        if (btn==0 || btn==1) {
            int ih=itemH(), startY=hY+18;
            List<SkinHistory.SkinEntry> hist=SkinHistory.getHistory();
            for (int i=0;i<hist.size();i++) {
                int iy=startY+i*ih-histScroll;
                if (mx>=hX+1 && mx<=hX+hW-1 && my>=iy && my<iy+ih) {
                    if (btn==0) loadHistory(hist.get(i)); else minecraft.setScreen(new SkinRenameScreen(this,hist.get(i))); return true; }
            }
        }
        return super.mouseClicked(mx,my,btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingModel && btn==0) {
            rotY+=(float)(mx-lastMX)*0.5f; rotX+=(float)(my-lastMY)*0.3f;
            rotX=Math.max(-70f,Math.min(70f,rotX)); lastMX=mx; lastMY=my; return true;
        }
        return super.mouseDragged(mx,my,btn,dx,dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        draggingModel=false; draggingFile=false;
        if (!droppedPaths.isEmpty()) { File f=new File(droppedPaths.get(0));
            if (validSkin(f)) { pendingFile=f; setStatus("โหลดไฟล์แล้ว กด 'เปลี่ยนสกิน'", SUCCESS); }
            else setStatus("ไฟล์ไม่ถูกต้อง (PNG 64–512px)", ERROR); droppedPaths.clear(); }
        return super.mouseReleased(mx,my,btn);
    }

    public void onFilesDragged(List<String> paths) { draggingFile=true; droppedPaths=new ArrayList<>(paths); }

    private void loadHistory(SkinHistory.SkinEntry e) {
        pendingFile=e.getFile(); isSlim=e.isSlim(); ModelPreferenceManager.setSlimPreference(isSlim);
        applySkin(pendingFile); rebuildWidgets();
    }

    @Override public boolean isPauseScreen() { return false; }
}
