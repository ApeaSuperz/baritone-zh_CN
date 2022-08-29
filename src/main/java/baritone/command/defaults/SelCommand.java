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
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForBlockOptionalMeta;
import baritone.api.command.datatypes.ForEnumFacing;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.schematic.*;
import baritone.api.selection.ISelection;
import baritone.api.selection.ISelectionManager;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.utils.IRenderer;
import baritone.utils.BlockStateInterface;
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class SelCommand extends Command {

    private ISelectionManager manager = baritone.getSelectionManager();
    private BetterBlockPos pos1 = null;
    private ISchematic clipboard = null;
    private Vec3i clipboardOffset = null;

    public SelCommand(IBaritone baritone) {
        super(baritone, "sel", "selection", "s");
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onRenderPass(RenderEvent event) {
                if (!Baritone.settings().renderSelectionCorners.value || pos1 == null) {
                    return;
                }
                Color color = Baritone.settings().colorSelectionPos1.value;
                float opacity = Baritone.settings().selectionOpacity.value;
                float lineWidth = Baritone.settings().selectionLineWidth.value;
                boolean ignoreDepth = Baritone.settings().renderSelectionIgnoreDepth.value;
                IRenderer.startLines(color, opacity, lineWidth, ignoreDepth);
                IRenderer.drawAABB(new AxisAlignedBB(pos1, pos1.add(1, 1, 1)));
                IRenderer.endLines(ignoreDepth);
            }
        });
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Action action = Action.getByName(args.getString());
        if (action == null) {
            throw new CommandInvalidTypeException(args.consumed(), "一种操作");
        }
        if (action == Action.POS1 || action == Action.POS2) {
            if (action == Action.POS2 && pos1 == null) {
                throw new CommandInvalidStateException("在使用位置 2 前请先设置位置 1");
            }
            BetterBlockPos playerPos = mc.getRenderViewEntity() != null ? BetterBlockPos.from(new BlockPos(mc.getRenderViewEntity())) : ctx.playerFeet();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            if (action == Action.POS1) {
                pos1 = pos;
                logDirect("位置 1 已设置");
            } else {
                manager.addSelection(pos1, pos);
                pos1 = null;
                logDirect("选区已增加");
            }
        } else if (action == Action.CLEAR) {
            args.requireMax(0);
            pos1 = null;
            logDirect(String.format("删除了 %d 个选区", manager.removeAllSelections().length));
        } else if (action == Action.UNDO) {
            args.requireMax(0);
            if (pos1 != null) {
                pos1 = null;
                logDirect("撤销位置 1");
            } else {
                ISelection[] selections = manager.getSelections();
                if (selections.length < 1) {
                    throw new CommandInvalidStateException("没有什么可以撤销！");
                } else {
                    pos1 = manager.removeSelection(selections[selections.length - 1]).pos1();
                    logDirect("撤销位置 2");
                }
            }
        } else if (action == Action.SET || action == Action.WALLS || action == Action.SHELL || action == Action.CLEARAREA || action == Action.REPLACE) {
            BlockOptionalMeta type = action == Action.CLEARAREA
                    ? new BlockOptionalMeta(Blocks.AIR)
                    : args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
            BlockOptionalMetaLookup replaces = null;
            if (action == Action.REPLACE) {
                args.requireMin(1);
                List<BlockOptionalMeta> replacesList = new ArrayList<>();
                replacesList.add(type);
                while (args.has(2)) {
                    replacesList.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
                }
                type = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
                replaces = new BlockOptionalMetaLookup(replacesList.toArray(new BlockOptionalMeta[0]));
            } else {
                args.requireMax(0);
            }
            ISelection[] selections = manager.getSelections();
            if (selections.length == 0) {
                throw new CommandInvalidStateException("无选区");
            }
            BetterBlockPos origin = selections[0].min();
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (ISelection selection : selections) {
                BetterBlockPos min = selection.min();
                origin = new BetterBlockPos(
                        Math.min(origin.x, min.x),
                        Math.min(origin.y, min.y),
                        Math.min(origin.z, min.z)
                );
            }
            for (ISelection selection : selections) {
                Vec3i size = selection.size();
                BetterBlockPos min = selection.min();
                ISchematic schematic = new FillSchematic(size.getX(), size.getY(), size.getZ(), type);
                if (action == Action.WALLS) {
                    schematic = new WallsSchematic(schematic);
                } else if (action == Action.SHELL) {
                    schematic = new ShellSchematic(schematic);
                } else if (action == Action.REPLACE) {
                    schematic = new ReplaceSchematic(schematic, replaces);
                }
                composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
            }
            baritone.getBuilderProcess().build("填充", composite, origin);
            logDirect("正在填充");
        } else if (action == Action.COPY) {
            BetterBlockPos playerPos = mc.getRenderViewEntity() != null ? BetterBlockPos.from(new BlockPos(mc.getRenderViewEntity())) : ctx.playerFeet();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            ISelection[] selections = manager.getSelections();
            if (selections.length < 1) {
                throw new CommandInvalidStateException("无选区");
            }
            BlockStateInterface bsi = new BlockStateInterface(ctx);
            BetterBlockPos origin = selections[0].min();
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (ISelection selection : selections) {
                BetterBlockPos min = selection.min();
                origin = new BetterBlockPos(
                        Math.min(origin.x, min.x),
                        Math.min(origin.y, min.y),
                        Math.min(origin.z, min.z)
                );
            }
            for (ISelection selection : selections) {
                Vec3i size = selection.size();
                BetterBlockPos min = selection.min();
                IBlockState[][][] blockstates = new IBlockState[size.getX()][size.getZ()][size.getY()];
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            blockstates[x][z][y] = bsi.get0(min.x + x, min.y + y, min.z + z);
                        }
                    }
                }
                ISchematic schematic = new StaticSchematic(){{
                    states = blockstates;
                    x = size.getX();
                    y = size.getY();
                    z = size.getZ();
                }};
                composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
            }
            clipboard = composite;
            clipboardOffset = origin.subtract(pos);
            logDirect("选区已复制");
        } else if (action == Action.PASTE) {
            BetterBlockPos playerPos = mc.getRenderViewEntity() != null ? BetterBlockPos.from(new BlockPos(mc.getRenderViewEntity())) : ctx.playerFeet();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            if (clipboard == null) {
                throw new CommandInvalidStateException("你需要先复制一个选区");
            }
            baritone.getBuilderProcess().build("填充", clipboard, pos.add(clipboardOffset));
            logDirect("正在建造");
        } else if (action == Action.EXPAND || action == Action.CONTRACT || action == Action.SHIFT) {
            args.requireExactly(3);
            TransformTarget transformTarget = TransformTarget.getByName(args.getString());
            if (transformTarget == null) {
                throw new CommandInvalidStateException("无效的变换类型");
            }
            EnumFacing direction = args.getDatatypeFor(ForEnumFacing.INSTANCE);
            int blocks = args.getAs(Integer.class);
            ISelection[] selections = manager.getSelections();
            if (selections.length < 1) {
                throw new CommandInvalidStateException("没有找到选区");
            }
            selections = transformTarget.transform(selections);
            for (ISelection selection : selections) {
                if (action == Action.EXPAND) {
                    manager.expand(selection, direction, blocks);
                } else if (action == Action.CONTRACT) {
                    manager.contract(selection, direction, blocks);
                } else {
                    manager.shift(selection, direction, blocks);
                }
            }
            logDirect(String.format("变换了 %d 个选区", selections.length));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .append(Action.getAllNames())
                    .filterPrefix(args.getString())
                    .sortAlphabetically()
                    .stream();
        } else {
            Action action = Action.getByName(args.getString());
            if (action != null) {
                if (action == Action.POS1 || action == Action.POS2) {
                    if (args.hasAtMost(3)) {
                        return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                    }
                } else if (action == Action.SET || action == Action.WALLS || action == Action.CLEARAREA || action == Action.REPLACE) {
                    if (args.hasExactlyOne() || action == Action.REPLACE) {
                        while (args.has(2)) {
                            args.get();
                        }
                        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
                    }
                } else if (action == Action.EXPAND || action == Action.CONTRACT || action == Action.SHIFT) {
                    if (args.hasExactlyOne()) {
                        return new TabCompleteHelper()
                                .append(TransformTarget.getAllNames())
                                .filterPrefix(args.getString())
                                .sortAlphabetically()
                                .stream();
                    } else {
                        TransformTarget target = TransformTarget.getByName(args.getString());
                        if (target != null && args.hasExactlyOne()) {
                            return args.tabCompleteDatatype(ForEnumFacing.INSTANCE);
                        }
                    }
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "类似 WorldEdit（创世神）的命令";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "sel 命令允许你操作 Baritone 的选区，类似于 WorldEdit（常译为创世神）。",
                "",
                "使用这些选区，你可以清除区域、用方块或其它东西填充它们。",
                "",
                "子命令 expand/contract/shift 使用一种选择器来确定选区，支持的选择器有 a/all（全部选区）、n/newest（最新）、o/oldest（最旧）。",
                "",
                "用法：",
                "> sel pos1/p1/1 - 将位置 1 设置为你当前位置",
                "> sel pos1/p1/1 <x> <y> <z> - 将位置 1 设置为 X,Y,Z",
                "> sel pos2/p2/2 - 将位置 2 设置为你当前位置",
                "> sel pos2/p2/2 <x> <y> <z> - 将位置 2 设置为 X,Y,Z",
                "",
                "> sel clear/c - 清除选区",
                "> sel undo/u - 撤销上一个操作（设置位置、创建选区等）",
                "> sel set/fill/s/f [方块] - 用方块完全填充所有选区",
                "> sel walls/w [方块] - 用指定方块填充选区的墙",
                "> sel shell/shl [方块] - 与 walls 相同，但还填充天花板和地板",
                "> sel cleararea/ca - 也就是 'set air'（用空气填充）",
                "> sel replace/r <被替换的方块...> <用于替换的方块> - 用另一种方块替换一种或多种方块",
                "> sel copy/cp <x> <y> <z> - 相对指定位置或你的位置，复制选区",
                "> sel paste/p <x> <y> <z> - 相对指定位置或你的位置，建造复制的选区",
                "",
                "> sel expand <选择器> <方向> <方块> - 扩展目标",
                "> sel contract <选择器> <方向> <方块> - 收缩目标",
                "> sel shift <选择器> <方向> <方块> - 移动目标（不改变大小）"
        );
    }

    enum Action {
        POS1("pos1", "p1", "1"),
        POS2("pos2", "p2", "2"),
        CLEAR("clear", "c"),
        UNDO("undo", "u"),
        SET("set", "fill", "s", "f"),
        WALLS("walls", "w"),
        SHELL("shell", "shl"),
        CLEARAREA("cleararea", "ca"),
        REPLACE("replace", "r"),
        EXPAND("expand", "ex"),
        COPY("copy", "cp"),
        PASTE("paste", "p"),
        CONTRACT("contract", "ct"),
        SHIFT("shift", "sh");
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

    enum TransformTarget {
        ALL(sels -> sels, "all", "a"),
        NEWEST(sels -> new ISelection[]{sels[sels.length - 1]}, "newest", "n"),
        OLDEST(sels -> new ISelection[]{sels[0]}, "oldest", "o");
        private final Function<ISelection[], ISelection[]> transform;
        private final String[] names;

        TransformTarget(Function<ISelection[], ISelection[]> transform, String... names) {
            this.transform = transform;
            this.names = names;
        }

        public ISelection[] transform(ISelection[] selections) {
            return transform.apply(selections);
        }

        public static TransformTarget getByName(String name) {
            for (TransformTarget target : TransformTarget.values()) {
                for (String alias : target.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return target;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (TransformTarget target : TransformTarget.values()) {
                names.addAll(Arrays.asList(target.names));
            }
            return names.toArray(new String[0]);
        }
    }
}
