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
import baritone.api.process.IGetToBlockProcess;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class BlacklistCommand extends Command {

    public BlacklistCommand(IBaritone baritone) {
        super(baritone, "blacklist");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        IGetToBlockProcess proc = baritone.getGetToBlockProcess();
        if (!proc.isActive()) {
            throw new CommandInvalidStateException("目前没有启动 GetToBlockProcess");
        }
        if (proc.blacklistClosest()) {
            logDirect("已将最近的实例加入黑名单");
        } else {
            throw new CommandInvalidStateException("无已知位置，无法加入黑名单");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "把最近的方块加入黑名单";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "当前往某种方块时，这个命令将最近的一个拉黑，Baritone 将不再尝试去到那里。",
                "",
                "用法：",
                "> blacklist"
        );
    }
}
