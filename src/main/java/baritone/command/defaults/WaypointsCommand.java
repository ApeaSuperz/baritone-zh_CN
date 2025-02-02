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
import baritone.api.cache.IWaypoint;
import baritone.api.cache.Waypoint;
import baritone.api.cache.IWorldData;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForWaypoints;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class WaypointsCommand extends Command {

    private Map<IWorldData,List<IWaypoint>> deletedWaypoints = new HashMap<>();

    public WaypointsCommand(IBaritone baritone) {
        super(baritone, "waypoints", "waypoint", "wp");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Action action = args.hasAny() ? Action.getByName(args.getString()) : Action.LIST;
        if (action == null) {
            throw new CommandInvalidTypeException(args.consumed(), "一种操作");
        }
        BiFunction<IWaypoint, Action, ITextComponent> toComponent = (waypoint, _action) -> {
            ITextComponent component = new TextComponentString("");
            ITextComponent tagComponent = new TextComponentString(waypoint.getTag().name() + " ");
            tagComponent.getStyle().setColor(TextFormatting.GRAY);
            String name = waypoint.getName();
            ITextComponent nameComponent = new TextComponentString(!name.isEmpty() ? name : "<空>");
            nameComponent.getStyle().setColor(!name.isEmpty() ? TextFormatting.GRAY : TextFormatting.DARK_GRAY);
            ITextComponent timestamp = new TextComponentString(" @ " + new Date(waypoint.getCreationTimestamp()));
            timestamp.getStyle().setColor(TextFormatting.DARK_GRAY);
            component.appendSibling(tagComponent);
            component.appendSibling(nameComponent);
            component.appendSibling(timestamp);
            component.getStyle()
                    .setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new TextComponentString("点击以选择")
                    ))
                    .setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s %s %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    _action.names[0],
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            ))
                    );
            return component;
        };
        Function<IWaypoint, ITextComponent> transform = waypoint ->
                toComponent.apply(waypoint, action == Action.LIST ? Action.INFO : action);
        if (action == Action.LIST) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag != null) {
                args.get();
            }
            IWaypoint[] waypoints = tag != null
                    ? ForWaypoints.getWaypointsByTag(this.baritone, tag)
                    : ForWaypoints.getWaypoints(this.baritone);
            if (waypoints.length > 0) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> logDirect(
                                tag != null
                                        ? String.format("标签 %s 的所有路点：", tag.name())
                                        : "所有路点："
                        ),
                        transform,
                        String.format(
                                "%s%s %s%s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names[0],
                                tag != null ? " " + tag.getName() : ""
                        )
                );
            } else {
                args.requireMax(0);
                throw new CommandInvalidStateException(
                        tag != null
                                ? "该标签未找到任何路点"
                                : "未找到路点"
                );
            }
        } else if (action == Action.SAVE) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag == null) {
                tag = IWaypoint.Tag.USER;
            } else {
                args.get();
            }
            String name = (args.hasExactlyOne() || args.hasExactly(4)) ? args.getString() : "";
            BetterBlockPos pos = args.hasAny()
                    ? args.getDatatypePost(RelativeBlockPos.INSTANCE, ctx.playerFeet())
                    : ctx.playerFeet();
            args.requireMax(0);
            IWaypoint waypoint = new Waypoint(name, tag, pos);
            ForWaypoints.waypoints(this.baritone).addWaypoint(waypoint);
            ITextComponent component = new TextComponentString("新增路点：");
            component.getStyle().setColor(TextFormatting.GRAY);
            component.appendSibling(toComponent.apply(waypoint, Action.INFO));
            logDirect(component);
        } else if (action == Action.CLEAR) {
            args.requireMax(1);
            IWaypoint.Tag tag = IWaypoint.Tag.getByName(args.getString());
            IWaypoint[] waypoints = ForWaypoints.getWaypointsByTag(this.baritone, tag);
            for (IWaypoint waypoint : waypoints) {
                ForWaypoints.waypoints(this.baritone).removeWaypoint(waypoint);
            }
            deletedWaypoints.computeIfAbsent(baritone.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>()).addAll(Arrays.<IWaypoint>asList(waypoints));
            ITextComponent textComponent = new TextComponentString(String.format("清除了 %d 个路点，点击可恢复它们", waypoints.length));
            textComponent.getStyle().setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    String.format(
                            "%s%s restore @ %s",
                            FORCE_COMMAND_PREFIX,
                            label,
                            Stream.of(waypoints).map(wp -> Long.toString(wp.getCreationTimestamp())).collect(Collectors.joining(" "))
                    )
            ));
            logDirect(textComponent);
        } else if (action == Action.RESTORE) {
            List<IWaypoint> waypoints = new ArrayList<>();
            List<IWaypoint> deletedWaypoints = this.deletedWaypoints.getOrDefault(baritone.getWorldProvider().getCurrentWorld(), Collections.emptyList());
            if (args.peekString().equals("@")) {
                args.get();
                // no args.requireMin(1) because if the user clears an empty tag there is nothing to restore
                while (args.hasAny()) {
                    long timestamp = args.getAs(Long.class);
                    for (IWaypoint waypoint : deletedWaypoints) {
                        if (waypoint.getCreationTimestamp() == timestamp) {
                            waypoints.add(waypoint);
                            break;
                        }
                    }
                }
            } else {
                args.requireExactly(1);
                int size = deletedWaypoints.size();
                int amount = Math.min(size, args.getAs(Integer.class));
                waypoints = new ArrayList<>(deletedWaypoints.subList(size - amount, size));
            }
            waypoints.forEach(ForWaypoints.waypoints(this.baritone)::addWaypoint);
            deletedWaypoints.removeIf(waypoints::contains);
            logDirect(String.format("恢复了 %d 个路点", waypoints.size()));
        } else {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint = null;
            if (args.hasAny() && args.peekString().equals("@")) {
                args.requireExactly(2);
                args.get();
                long timestamp = args.getAs(Long.class);
                for (IWaypoint iWaypoint : waypoints) {
                    if (iWaypoint.getCreationTimestamp() == timestamp) {
                        waypoint = iWaypoint;
                        break;
                    }
                }
                if (waypoint == null) {
                    throw new CommandInvalidStateException("指定了时间戳，但未找到路点");
                }
            } else {
                switch (waypoints.length) {
                    case 0:
                        throw new CommandInvalidStateException("未找到路点");
                    case 1:
                        waypoint = waypoints[0];
                        break;
                    default:
                        break;
                }
            }
            if (waypoint == null) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> logDirect("找到多个路点："),
                        transform,
                        String.format(
                                "%s%s %s %s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names[0],
                                args.consumedString()
                        )
                );
            } else {
                if (action == Action.INFO) {
                    logDirect(transform.apply(waypoint));
                    logDirect(String.format("位置：%s", waypoint.getLocation()));
                    ITextComponent deleteComponent = new TextComponentString("点击删除这个路点");
                    deleteComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s delete %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            )
                    ));
                    ITextComponent goalComponent = new TextComponentString("点击将目标设置为这个路点");
                    goalComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s goal %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            )
                    ));
                    ITextComponent recreateComponent = new TextComponentString("点击显示重新创建该路点的命令");
                    recreateComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            String.format(
                                    "%s%s save %s %s %s %s %s",
                                    Baritone.settings().prefix.value, // This uses the normal prefix because it is run by the user.
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getName(),
                                    waypoint.getLocation().x,
                                    waypoint.getLocation().y,
                                    waypoint.getLocation().z
                            )
                    ));
                    ITextComponent backComponent = new TextComponentString("点击返回路点列表");
                    backComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s list",
                                    FORCE_COMMAND_PREFIX,
                                    label
                            )
                    ));
                    logDirect(deleteComponent);
                    logDirect(goalComponent);
                    logDirect(recreateComponent);
                    logDirect(backComponent);
                } else if (action == Action.DELETE) {
                    ForWaypoints.waypoints(this.baritone).removeWaypoint(waypoint);
                    deletedWaypoints.computeIfAbsent(baritone.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>()).add(waypoint);
                    ITextComponent textComponent = new TextComponentString("该路点已成功删除，点击可恢复");
                    textComponent.getStyle().setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s restore @ %s",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getCreationTimestamp()
                            )
                    ));
                    logDirect(textComponent);
                } else if (action == Action.GOAL) {
                    Goal goal = new GoalBlock(waypoint.getLocation());
                    baritone.getCustomGoalProcess().setGoal(goal);
                    logDirect(String.format("目标：%s", goal));
                } else if (action == Action.GOTO) {
                    Goal goal = new GoalBlock(waypoint.getLocation());
                    baritone.getCustomGoalProcess().setGoalAndPath(goal);
                    logDirect(String.format("正在前往：%s", goal));
                }
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            if (args.hasExactlyOne()) {
                return new TabCompleteHelper()
                        .append(Action.getAllNames())
                        .sortAlphabetically()
                        .filterPrefix(args.getString())
                        .stream();
            } else {
                Action action = Action.getByName(args.getString());
                if (args.hasExactlyOne()) {
                    if (action == Action.LIST || action == Action.SAVE || action == Action.CLEAR) {
                        return new TabCompleteHelper()
                                .append(IWaypoint.Tag.getAllNames())
                                .sortAlphabetically()
                                .filterPrefix(args.getString())
                                .stream();
                    } else if (action == Action.RESTORE) {
                        return Stream.empty();
                    } else {
                        return args.tabCompleteDatatype(ForWaypoints.INSTANCE);
                    }
                } else if (args.has(3) && action == Action.SAVE) {
                    args.get();
                    args.get();
                    return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "管理路点";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "waypoint 命令允许你管理 Baritone 的路点。",
                "",
                "路点可用于标记位置，以便以后使用。每个路点都有一个标签和一个可选的名称。",
                "",
                "请注意，子命令 info、delete、goal 允许你按标签指定路点。如果多个路点具有相同标签，那么将让你选择你指的是哪一个。",
                "",
                "缺少参数的 save 子命令默认使用 USER 标签创建一个未命名路点并使用你当前的位置。",
                "",
                "用法：",
                "> wp [l/list] - 列出所有路点",
                "> wp <l/list> <标签> - 按标签列出所有路点",
                "> wp <s/save> - 在你当前位置保存一个未命名的 USER 路点",
                "> wp <s/save> [标签] [名称] [位置] - 使用指定的标签、名称、位置保存一个路点",
                "> wp <i/info/show> <标签/名称> - 按标签或名称显示一个路点的信息",
                "> wp <d/delete> <标签/名称> - 按标签或名称删除一个路点",
                "> wp <restore> <n> - 恢复最后被删除的 n 个路点",
                "> wp <c/clear> <标签> - 删除所有带指定标签的路点",
                "> wp <g/goal> <标签/名称> - 通过标签或名称将目标设置为一个路点",
                "> wp <goto> <标签/名称> - 通过标签或名称将目标设置为一个路点，并开始寻路"
        );
    }

    private enum Action {
        LIST("list", "get", "l"),
        CLEAR("clear", "c"),
        SAVE("save", "s"),
        INFO("info", "show", "i"),
        DELETE("delete", "d"),
        RESTORE("restore"),
        GOAL("goal", "g"),
        GOTO("goto");
        private final String[] names;

        Action(String... names) {
            this.names = names;
        }

        public static Action getByName(String name) {
            for (Action action : Action.values()) {
                for (String alias : action.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (Action action : Action.values()) {
                names.addAll(Arrays.asList(action.names));
            }
            return names.toArray(new String[0]);
        }
    }
}
