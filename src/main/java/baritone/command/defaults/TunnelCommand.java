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

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalStrictDirection;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class TunnelCommand extends Command {

    public TunnelCommand(IBaritone baritone) {
        super(baritone, "tunnel");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(3);
        if (args.hasExactly(3)) {
            boolean cont = true;
            int height = Integer.parseInt(args.getArgs().get(0).getValue());
            int width = Integer.parseInt(args.getArgs().get(1).getValue());
            int depth = Integer.parseInt(args.getArgs().get(2).getValue());

            if (width < 1 || height < 2 || depth < 1 || height > 255) {
                logDirect("宽度和深度至少为 1 个方块；高度至少 2 个方块且不能大于建造限制。");
                cont = false;
            }

            if (cont) {
                height--;
                width--;
                BlockPos corner1;
                BlockPos corner2;
                EnumFacing enumFacing = ctx.player().getHorizontalFacing();
                int addition = ((width % 2 == 0) ? 0 : 1);
                switch (enumFacing) {
                    case EAST:
                        corner1 = new BlockPos(ctx.playerFeet().x, ctx.playerFeet().y, ctx.playerFeet().z - width / 2);
                        corner2 = new BlockPos(ctx.playerFeet().x + depth, ctx.playerFeet().y + height, ctx.playerFeet().z + width / 2 + addition);
                        break;
                    case WEST:
                        corner1 = new BlockPos(ctx.playerFeet().x, ctx.playerFeet().y, ctx.playerFeet().z + width / 2 + addition);
                        corner2 = new BlockPos(ctx.playerFeet().x - depth, ctx.playerFeet().y + height, ctx.playerFeet().z - width / 2);
                        break;
                    case NORTH:
                        corner1 = new BlockPos(ctx.playerFeet().x - width / 2, ctx.playerFeet().y, ctx.playerFeet().z);
                        corner2 = new BlockPos(ctx.playerFeet().x + width / 2 + addition, ctx.playerFeet().y + height, ctx.playerFeet().z - depth);
                        break;
                    case SOUTH:
                        corner1 = new BlockPos(ctx.playerFeet().x + width / 2 + addition, ctx.playerFeet().y, ctx.playerFeet().z);
                        corner2 = new BlockPos(ctx.playerFeet().x - width / 2, ctx.playerFeet().y + height, ctx.playerFeet().z + depth);
                        break;
                    default:
                        throw new IllegalStateException("非预期值：" + enumFacing);
                }
                logDirect(String.format("正在创建一个高 %d 格，宽 %d 格，深 %d 格的隧道", height + 1, width + 1, depth));
                baritone.getBuilderProcess().clearArea(corner1, corner2);
            }
        } else {
            Goal goal = new GoalStrictDirection(
                    ctx.playerFeet(),
                    ctx.player().getHorizontalFacing()
            );
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
            logDirect(String.format("目标：%s", goal.toString()));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "设定一个目标，在你目前的方向上开辟隧道";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "tunnel 命令设置一个目标，告诉 Baritone 沿着你面对的方向直线挖掘。",
                "",
                "用法：",
                "> tunnel - 无参数，挖掘宽高为 1x2",
                "> tunnel <高度> <宽度> <深度> - 开辟用户定义高度、宽度、深度（长度）的隧道"
        );
    }
}
