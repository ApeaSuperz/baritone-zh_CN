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
import baritone.api.command.datatypes.RelativeCoordinate;
import baritone.api.command.datatypes.RelativeGoal;
import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.utils.BetterBlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GoalCommand extends Command {

    public GoalCommand(IBaritone baritone) {
        super(baritone, "goal");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        ICustomGoalProcess goalProcess = baritone.getCustomGoalProcess();
        if (args.hasAny() && Arrays.asList("reset", "clear", "none").contains(args.peekString())) {
            args.requireMax(1);
            if (goalProcess.getGoal() != null) {
                goalProcess.setGoal(null);
                logDirect("已清除目标");
            } else {
                logDirect("没有可以清除的目标");
            }
        } else {
            args.requireMax(3);
            BetterBlockPos origin = baritone.getPlayerContext().playerFeet();
            Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
            goalProcess.setGoal(goal);
            logDirect(String.format("目标：%s", goal.toString()));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("reset", "clear", "none", "~");
        } else {
            if (args.hasAtMost(3)) {
                while (args.has(2)) {
                    if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) == null) {
                        break;
                    }
                    args.get();
                    if (!args.has(2)) {
                        helper.append("~");
                    }
                }
            }
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return "设定或清除目标";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "goal 命令允许你设置或清除 Baritone 的目标。",
                "",
                "在任何需要坐标的地方，你都可以像常规 Minecraft 命令一样使用 ~ 符号。你也可以直接使用常规数字。",
                "",
                "用法：",
                "> goal - 将目标设置为当前位置",
                "> goal <reset/clear/none> - 擦除目标",
                "> goal <y> - 将目标设置为 Y 层",
                "> goal <x> <z> - 将目标设置为 X,Z 位置",
                "> goal <x> <y> <z> - 将目标设置为 X,Y,Z 位置"
        );
    }
}
