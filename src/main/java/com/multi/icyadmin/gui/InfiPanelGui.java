package com.multi.icyadmin.gui;

import com.multi.icyadmin.Core;
import com.multi.icyadmin.data.ActionsEnum;
import com.multi.icyadmin.data.ItemListNode;
import com.multi.icyadmin.data.MenuElement;
import com.multi.icyadmin.data.RequestEnum;
import com.multi.icyadmin.gui.elements.ItemList;
import com.multi.icyadmin.handlers.packets.RequestPacket;
import com.multi.icyadmin.handlers.packets.SendCommandPacket;
import com.multi.icyadmin.utils.GLUtils;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by MultiMote on 02.01.2015.
 */
public class InfiPanelGui extends GuiScreen {

    protected int x_pos = 13;
    protected int y_pos = 5;
    protected int x_pos_right = 0;
    protected int y_pos_bottom = 0;
    protected int x_pos_separator = 0;

    protected int updateTimer;

    private ItemList playerList;
    private ItemList itemsList;
    private String playerSelected;

    private List<ItemListNode> menus = new ArrayList<ItemListNode>();
    private List<ItemListNode> playersOnline = new ArrayList<ItemListNode>();

    private ArgsGetGui askGui;

    private String cur_menu = "MAIN_PAGE";

    public InfiPanelGui() {
        askGui = new ArgsGetGui(this);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        x_pos_right = this.width - 13;
        y_pos_bottom = this.height - 23;
        x_pos_separator = x_pos + 100;


        playerList = new ItemList(this, mc, x_pos + 1, y_pos, x_pos_separator - x_pos, y_pos_bottom - y_pos, playersOnline);
        itemsList = new ItemList(this, mc, x_pos_separator + 1, y_pos, x_pos_right - x_pos_separator - 1, y_pos_bottom - y_pos, menus);


        if (askGui.isVisible()) askGui.initGui();

        reloadMenu(true);

        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        askGui.setVisible(false);
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (updateTimer-- < 0) {
            updatePlayerList();
            updateTimer = 40;
        }
    }

    public void updatePlayerList() {
        playersOnline.clear();
        NetHandlerPlayClient nethandlerplayclient = this.mc.thePlayer.sendQueue;
        List playerInfoList = nethandlerplayclient.playerInfoList;
        int maxPlayers = nethandlerplayclient.currentServerMaxPlayers;

        playersOnline.add(ItemListNode.separator());

        playersOnline.add(
                ItemListNode.create(
                        I18n.format("gui.iadmin.players",
                                Integer.toString(playerInfoList.size()),
                                Integer.toString(maxPlayers))
                ).setColor(0xCED6DE).setCanBeSelected(false));

        for (Object obj : playerInfoList) {
            ItemListNode node = ItemListNode.create(((GuiPlayerInfo) obj).name);
            node.setType(ActionsEnum.PLAYER);
            node.setClickEventFired(true);
            node.setColor(0x1F76D3);
            playersOnline.add(node);
        }
    }

    public void reloadMenu(boolean request) {
        menus.clear();
        itemsList.setIndexSelected(0);
        for (MenuElement node : Core.dynStorage.menus) {
            if (node.menu.equals(cur_menu)) menus.add(node.action);
        }

        List<ItemListNode> tmenus = new ArrayList<ItemListNode>(menus);
        for (ItemListNode listnode : tmenus) {
            RequestEnum inc = listnode.getInclude();
            if (inc != null) {
                if (request) {
                    Core.packets.sendToServer(new RequestPacket(inc));
                    menus.add(ItemListNode.title(I18n.format("icyadmin.receiving")));
                } else {
                    for (MenuElement node : Core.dynStorage.menus) {
                        if (node.menu.equals("$INC_" + inc)) menus.add(node.action);
                    }
                }
            }
        }
    }

    public void flushIncludes(RequestEnum inc) {
        ArrayList<MenuElement> mcopy = new ArrayList<MenuElement>(Core.dynStorage.menus);
        for (MenuElement node : mcopy) {
            if (node.menu.equals("$INC_" + inc)) Core.dynStorage.menus.remove(node);
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int mb) {
        if (askGui.isVisible()) {
            askGui.mouseClicked(mx, my, mb);
        } else {
            playerList.mouseClicked(mx, my);
            itemsList.mouseClicked(mx, my);
            super.mouseClicked(mx, my, mb);
        }
    }

    @Override
    protected void keyTyped(char keyChar, int keyId) {
        if (askGui.isVisible()) {
            askGui.keyTyped(keyChar, keyId);
        } else super.keyTyped(keyChar, keyId);
    }

    public void elementClicked(ItemListNode node) {
        if (node.getType() == ActionsEnum.PLAYER) {
            playerSelected = node.getName();
        }
    }

    public void elementDoubleClicked(ItemListNode node) {
        if (node.getType() == ActionsEnum.PAGE) {
            cur_menu = node.getCommandData();
            reloadMenu(true);
        } else if (node.getType() == ActionsEnum.LINK) {
            try {
                Class oclass = Class.forName("java.awt.Desktop");
                Object object = oclass.getMethod("getDesktop", new Class[0]).invoke(null);
                oclass.getMethod("browse", new Class[]{URI.class}).invoke(object, new URI(node.getCommandData()));
            } catch (Throwable throwable) {
                Core.logger.error("Couldn\'t open link", throwable);
            }
        } else if (node.getType() == ActionsEnum.CMD_EXEC) {
            if (!Core.proxy.canClientUsePanel()) {
                printNoPerms();
                return;
            }
            if (node.getCommandData() != null) {
                String cmd = node.getCommandData().replaceAll("%me%", mc.thePlayer.getCommandSenderName());
                if (cmd.contains("%player%")) {
                    if (playerSelected == null) {
                        printChatError("Select target player!");
                        return;
                    }
                    cmd = cmd.replaceAll("%player%", playerSelected);
                }

                List<String> variables = new ArrayList<String>();

                Matcher matcher = Pattern.compile("\\$(\\S+)").matcher(cmd);
                while (matcher.find()) {
                    String s = matcher.group(1);
                    if (!variables.contains(s)) variables.add(s);
                }

                if (!variables.isEmpty()) {
                    Core.logger.info("Let's ask user for these: " + variables);
                    askGui.setCommand(cmd);
                    askGui.setParsedVars(variables);
                    askGui.setVisible(true);
                    return;
                }

                splitAndExecChatCmd(cmd);

            } else Core.logger.error("Can't execute null command.");

        } else if (node.getType() != ActionsEnum.NOTHING && node.getType() != ActionsEnum.PLAYER) {
            if (!Core.proxy.canClientUsePanel()) {
                printNoPerms();
                return;
            }
            if (Core.actionHandler.clientCheck(playerSelected, mc.thePlayer, null, node.getType())) {
                Core.packets.sendToServer(new SendCommandPacket(playerSelected, node.getType()));
                Core.packets.sendToServer(new RequestPacket(RequestEnum.SEND_ACTION, "toggled " + node.getType().toString()));

            }
        }
    }


    public void argsConfirmed() {
        if (!Core.proxy.canClientUsePanel()) {
            printNoPerms();
            return;
        }
        splitAndExecChatCmd(askGui.makeCmd());
    }


    public void splitAndExecChatCmd(String cmd) {
        if (cmd.contains(">>")) {
            String[] split = cmd.split("\\(>>\\)");
            for (String s : split) execChatCmd(s);
        } else execChatCmd(cmd);
    }

    public void execChatCmd(String cmd) {
        Core.packets.sendToServer(new RequestPacket(RequestEnum.SEND_ACTION, "executed command (" + cmd + ")"));
        Core.logger.info("Executing " + cmd);
        this.mc.thePlayer.sendChatMessage("/" + cmd);
    }

    public void printNoPerms() {
        mc.thePlayer.addChatMessage(new ChatComponentTranslation("icyadmin.permissions").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
    }

    public void printChatError(String msg) {
        mc.thePlayer.addChatMessage(new ChatComponentText("[IcyErrClient] " + msg).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));

    }


    @Override
    public void drawScreen(int mx, int my, float f) {
        GLUtils.drawBorderedRect(x_pos, y_pos, x_pos_right, y_pos_bottom, 0xBB000000, 1, 0xFF747474);
        drawRect(x_pos_separator, y_pos, x_pos_separator + 1, y_pos_bottom, 0xFF747474);

        playerList.drawList(mx, my, f, askGui.isVisible());
        itemsList.drawList(mx, my, f, askGui.isVisible());
        super.drawScreen(mx, my, f);

        askGui.draw(mx, my, f);
    }
}
