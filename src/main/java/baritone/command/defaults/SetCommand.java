/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.utils.SettingsUtil;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;
import static baritone.api.utils.SettingsUtil.*;

public class SetCommand extends Command {

    public SetCommand(IBaritone baritone) {
        super(baritone, "set", "setting", "settings");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String arg = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";
        if (Arrays.asList("s", "save").contains(arg)) {
            SettingsUtil.save(Baritone.settings());
            logDirect("设置已保存");
            return;
        }
        boolean viewModified = Arrays.asList("m", "mod", "modified").contains(arg);
        boolean viewAll = Arrays.asList("all", "l", "list").contains(arg);
        boolean paginate = viewModified || viewAll;
        if (paginate) {
            String search = args.hasAny() && args.peekAsOrNull(Integer.class) == null ? args.getString() : "";
            args.requireMax(1);
            List<? extends Settings.Setting> toPaginate =
                    (viewModified ? SettingsUtil.modifiedSettings(Baritone.settings()) : Baritone.settings().allSettings).stream()
                            .filter(s -> !javaOnlySetting(s))
                            .filter(s -> s.getName().toLowerCase(Locale.US).contains(search.toLowerCase(Locale.US)))
                            .sorted((s1, s2) -> String.CASE_INSENSITIVE_ORDER.compare(s1.getName(), s2.getName()))
                            .collect(Collectors.toList());
            Paginator.paginate(
                    args,
                    new Paginator<>(toPaginate),
                    () -> logDirect(
                            !search.isEmpty()
                                    ? String.format("所有%s包含字符串 '%s' 的设置：", viewModified ? "修改过且" : "", search)
                                    : String.format("所有%s设置：", viewModified ? "修改过的" : "")
                    ),
                    setting -> {
                        ITextComponent typeComponent = new TextComponentString(String.format(
                                " (%s)",
                                settingTypeToString(setting)
                        ));
                        typeComponent.getStyle().setColor(TextFormatting.DARK_GRAY);
                        ITextComponent hoverComponent = new TextComponentString("");
                        hoverComponent.getStyle().setColor(TextFormatting.GRAY);
                        hoverComponent.appendText(setting.getName());
                        hoverComponent.appendText(String.format("\n类型：%s", settingTypeToString(setting)));
                        hoverComponent.appendText(String.format("\n\n值：\n%s", settingValueToString(setting)));
                        hoverComponent.appendText(String.format("\n\n默认值：\n%s", settingDefaultToString(setting)));
                        String commandSuggestion = Baritone.settings().prefix.value + String.format("set %s ", setting.getName());
                        ITextComponent component = new TextComponentString(setting.getName());
                        component.getStyle().setColor(TextFormatting.GRAY);
                        component.appendSibling(typeComponent);
                        component.getStyle()
                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent))
                                .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandSuggestion));
                        return component;
                    },
                    FORCE_COMMAND_PREFIX + "set " + arg + " " + search
            );
            return;
        }
        args.requireMax(1);
        boolean resetting = arg.equalsIgnoreCase("reset");
        boolean toggling = arg.equalsIgnoreCase("toggle");
        boolean doingSomething = resetting || toggling;
        if (resetting) {
            if (!args.hasAny()) {
                logDirect("请将 'all' 指定为 reset 的参数，以确认你确实想要这样做：");
                logDirect("*所有*设置都将被重置，使用 'set modified' 或 'modified' 命令查看会被重置的内容。");
                logDirect("指定一个设置项名称而非 'all'，可以仅重置一个设置项。");
            } else if (args.peekString().equalsIgnoreCase("all")) {
                SettingsUtil.modifiedSettings(Baritone.settings()).forEach(Settings.Setting::reset);
                logDirect("所有设置都已重置为其默认值");
                SettingsUtil.save(Baritone.settings());
                return;
            }
        }
        if (toggling) {
            args.requireMin(1);
        }
        String settingName = doingSomething ? args.getString() : arg;
        Settings.Setting<?> setting = Baritone.settings().allSettings.stream()
                .filter(s -> s.getName().equalsIgnoreCase(settingName))
                .findFirst()
                .orElse(null);
        if (setting == null) {
            throw new CommandInvalidTypeException(args.consumed(), "一个有效的设置项");
        }
        if (javaOnlySetting(setting)) {
            // ideally it would act as if the setting didn't exist
            // but users will see it in Settings.java or its javadoc
            // so at some point we have to tell them or they will see it as a bug
            throw new CommandInvalidStateException(String.format("设置项 %s 只能通过 API 使用。", setting.getName()));
        }
        if (!doingSomething && !args.hasAny()) {
            logDirect(String.format("设置项 %s 的值：", setting.getName()));
            logDirect(settingValueToString(setting));
        } else {
            String oldValue = settingValueToString(setting);
            if (resetting) {
                setting.reset();
            } else if (toggling) {
                if (setting.getValueClass() != Boolean.class) {
                    throw new CommandInvalidTypeException(args.consumed(), "一个可切换的设置", "其它设置");
                }
                //noinspection unchecked
                ((Settings.Setting<Boolean>) setting).value ^= true;
                logDirect(String.format(
                        "已将设置 %s 切换为 %s",
                        setting.getName(),
                        Boolean.toString((Boolean) setting.value)
                ));
            } else {
                String newValue = args.getString();
                try {
                    SettingsUtil.parseAndApply(Baritone.settings(), arg, newValue);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new CommandInvalidTypeException(args.consumed(), "一个有效的设置项", t);
                }
            }
            if (!toggling) {
                logDirect(String.format(
                        "成功将 %s %s为 %s",
                        setting.getName(),
                        resetting ? "重置" : "设置",
                        settingValueToString(setting)
                ));
            }
            ITextComponent oldValueComponent = new TextComponentString(String.format("旧值：%s", oldValue));
            oldValueComponent.getStyle()
                    .setColor(TextFormatting.GRAY)
                    .setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new TextComponentString("点击将设置恢复为此值")
                    ))
                    .setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            FORCE_COMMAND_PREFIX + String.format("set %s %s", setting.getName(), oldValue)
                    ));
            logDirect(oldValueComponent);
            if ((setting.getName().equals("chatControl") && !(Boolean) setting.value && !Baritone.settings().chatControlAnyway.value) ||
                    setting.getName().equals("chatControlAnyway") && !(Boolean) setting.value && !Baritone.settings().chatControl.value) {
                logDirect("警告：聊天命令将不再有效。如果你想恢复此更改，请使用前缀控制（如果启用）或点击上方列出的旧值。", TextFormatting.RED);
            } else if (setting.getName().equals("prefixControl") && !(Boolean) setting.value) {
                logDirect("警告：带前缀的命令将不再有效。如果你想恢复此更改，请使用聊天控制（如果启用）或点击上方列出的旧值。", TextFormatting.RED);
            }
        }
        SettingsUtil.save(Baritone.settings());
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            String arg = args.getString();
            if (args.hasExactlyOne() && !Arrays.asList("s", "save").contains(args.peekString().toLowerCase(Locale.US))) {
                if (arg.equalsIgnoreCase("reset")) {
                    return new TabCompleteHelper()
                            .addModifiedSettings()
                            .prepend("all")
                            .filterPrefix(args.getString())
                            .stream();
                } else if (arg.equalsIgnoreCase("toggle")) {
                    return new TabCompleteHelper()
                            .addToggleableSettings()
                            .filterPrefix(args.getString())
                            .stream();
                }
                Settings.Setting setting = Baritone.settings().byLowerName.get(arg.toLowerCase(Locale.US));
                if (setting != null) {
                    if (setting.getType() == Boolean.class) {
                        TabCompleteHelper helper = new TabCompleteHelper();
                        if ((Boolean) setting.value) {
                            helper.append("true", "false");
                        } else {
                            helper.append("false", "true");
                        }
                        return helper.filterPrefix(args.getString()).stream();
                    } else {
                        return Stream.of(settingValueToString(setting));
                    }
                }
            } else if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .addSettings()
                        .sortAlphabetically()
                        .prepend("list", "modified", "reset", "toggle", "save")
                        .filterPrefix(arg)
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "查看或更改设置";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "使用 set 命令，你可以管理 Baritone 的所有设置。几乎每个方面都由这些设置来控制，尽情发挥吧！",
                "",
                "用法：",
                "> set - 与 `set list` 相同",
                "> set list [页码] - 查看所有设置",
                "> set modified [页码] - 查看修改过的设置",
                "> set <设置项> - 查看一项设置当前的值",
                "> set <设置项> <值> - 设置一项设置的值",
                "> set reset all - 重置*所有设置*为它们的默认值",
                "> set reset <设置项> - 重置一项设置为它的默认值",
                "> set toggle <设置项> - 切换布尔设置",
                "> set save - 保存所有设置（然而这是自动的）"
        );
    }
}
