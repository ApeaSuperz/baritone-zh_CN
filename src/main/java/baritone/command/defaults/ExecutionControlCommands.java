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
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Contains the pause, resume, and paused commands.
 * <p>
 * This thing is scoped to hell, private so far you can't even access it using reflection, because you AREN'T SUPPOSED
 * TO USE THIS to pause and resume Baritone. Make your own process that returns {@link PathingCommandType#REQUEST_PAUSE
 * REQUEST_PAUSE} as needed.
 */
public class ExecutionControlCommands {

    Command pauseCommand;
    Command resumeCommand;
    Command pausedCommand;
    Command cancelCommand;

    public ExecutionControlCommands(IBaritone baritone) {
        // array for mutability, non-field so reflection can't touch it
        final boolean[] paused = {false};
        baritone.getPathingControlManager().registerProcess(
                new IBaritoneProcess() {
                    @Override
                    public boolean isActive() {
                        return paused[0];
                    }

                    @Override
                    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }

                    @Override
                    public boolean isTemporary() {
                        return true;
                    }

                    @Override
                    public void onLostControl() {
                    }

                    @Override
                    public double priority() {
                        return DEFAULT_PRIORITY + 1;
                    }

                    @Override
                    public String displayName0() {
                        return "暂停/恢复 命令";
                    }
                }
        );
        pauseCommand = new Command(baritone, "pause", "p", "paws") {
            @Override
            public void execute(String label, IArgConsumer args) throws CommandException {
                args.requireMax(0);
                if (paused[0]) {
                    throw new CommandInvalidStateException("已经暂停");
                }
                paused[0] = true;
                logDirect("已暂停");
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return "暂停 Baritone，直到你使用 resume";
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        "此命令告诉 Baritone 暂时停下它手头的事。",
                        "",
                        "这可以用来暂停寻路、建造、跟随等等。只需使用 resume 命令，就可以再次启动它！",
                        "",
                        "用法：",
                        "> pause"
                );
            }
        };
        resumeCommand = new Command(baritone, "resume", "r", "unpause", "unpaws") {
            @Override
            public void execute(String label, IArgConsumer args) throws CommandException {
                args.requireMax(0);
                baritone.getBuilderProcess().resume();
                if (!paused[0]) {
                    throw new CommandInvalidStateException("无暂停");
                }
                paused[0] = false;
                logDirect("已恢复");
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return "在某个暂停后恢复 Baritone";
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        "此命令告诉 Baritone 恢复上次使用 pause 时正在做的事情。",
                        "",
                        "用法：",
                        "> resume"
                );
            }
        };
        pausedCommand = new Command(baritone, "paused") {
            @Override
            public void execute(String label, IArgConsumer args) throws CommandException {
                args.requireMax(0);
                logDirect(String.format("Baritone %s暂停", paused[0] ? "已" : "没有"));
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return "告诉你 Baritone 是否暂停";
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        "此命令告诉你当前是否使用 pause 命令暂停了 Baritone。",
                        "",
                        "用法：",
                        "> paused"
                );
            }
        };
        cancelCommand = new Command(baritone, "cancel", "c", "stop") {
            @Override
            public void execute(String label, IArgConsumer args) throws CommandException {
                args.requireMax(0);
                if (paused[0]) {
                    paused[0] = false;
                }
                baritone.getPathingBehavior().cancelEverything();
                logDirect("好的，取消了");
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return "取消 Baritone 手头的事";
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        "此命令告诉 Baritone 停止它目前正在做的事情。",
                        "",
                        "用法：",
                        "> cancel"
                );
            }
        };
    }
}
