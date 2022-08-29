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
import baritone.api.command.datatypes.RelativeFile;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ExploreFilterCommand extends Command {

    public ExploreFilterCommand(IBaritone baritone) {
        super(baritone, "explorefilter");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        File file = args.getDatatypePost(RelativeFile.INSTANCE, mc.gameDir.getAbsoluteFile().getParentFile());
        boolean invert = false;
        if (args.hasAny()) {
            if (args.getString().equalsIgnoreCase("invert")) {
                invert = true;
            } else {
                throw new CommandInvalidTypeException(args.consumed(), " 'invert'，要么不填");
            }
        }
        try {
            baritone.getExploreProcess().applyJsonFilter(file.toPath().toAbsolutePath(), invert);
        } catch (NoSuchFileException e) {
            throw new CommandInvalidStateException("找不到文件");
        } catch (JsonSyntaxException e) {
            throw new CommandInvalidStateException("JSON 语法无效");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        logDirect(String.format("探索过滤器已生效。反选：%s", invert ? "是" : "否"));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return RelativeFile.tabComplete(args, RelativeFile.gameDir());
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "根据 JSON 文件探索区块";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "在使用 explore 之前，应用一个探索过滤器，它告诉探索进程哪些区块已经被探索/未被探索。",
                "",
                "JSON 文件遵循以下格式：[{\"x\":0,\"z\":0},...]",
                "",
                "如果指定了 'invert'，列出的区块将被视为*未*探索，而不是已探索。",
                "",
                "用法：",
                "> explorefilter <路径> [invert] - 加载指定路径所引用的 JSON 文件。如果要指定反选，它只能是一字不差的 'invert'"
        );
    }
}
