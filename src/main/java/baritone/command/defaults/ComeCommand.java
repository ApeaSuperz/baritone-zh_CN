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
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ComeCommand extends Command {

    public ComeCommand(IBaritone baritone) {
        super(baritone, "come");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) {
            throw new CommandInvalidStateException("提供视野的实体为 null");
        }
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(new BlockPos(entity)));
        logDirect("来啦");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "开始朝你的相机前进";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "come 命令告诉 Baritone 来到你的相机（视野）位置。",
                "",
                "在一些黑客客户端中非常有用，因为自由相机（Freecam，常被称作灵魂出窍）不会移动你角色的位置。",
                "",
                "用法：",
                "> come"
        );
    }
}
