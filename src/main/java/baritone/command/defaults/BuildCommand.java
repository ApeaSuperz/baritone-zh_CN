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
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.datatypes.RelativeFile;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class BuildCommand extends Command {

    private static final File schematicsDir = new File(Minecraft.getMinecraft().gameDir, "schematics");

    public BuildCommand(IBaritone baritone) {
        super(baritone, "build");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        File file = args.getDatatypePost(RelativeFile.INSTANCE, schematicsDir).getAbsoluteFile();
        if (FilenameUtils.getExtension(file.getAbsolutePath()).isEmpty()) {
            file = new File(file.getAbsolutePath() + "." + Baritone.settings().schematicFallbackExtension.value);
        }
        BetterBlockPos origin = ctx.playerFeet();
        BetterBlockPos buildOrigin;
        if (args.hasAny()) {
            args.requireMax(3);
            buildOrigin = args.getDatatypePost(RelativeBlockPos.INSTANCE, origin);
        } else {
            args.requireMax(0);
            buildOrigin = origin;
        }
        boolean success = baritone.getBuilderProcess().build(file.getName(), file, buildOrigin);
        if (!success) {
            throw new CommandInvalidStateException("不能加载示意图。确保使用的是完整文件名，包含拓展名（例如 blah.schematic）。");
        }
        logDirect(String.format("成功加载建造示意图\n原点：%s", buildOrigin));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return RelativeFile.tabComplete(args, schematicsDir);
        } else if (args.has(2)) {
            args.get();
            return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "建造一个示意图";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "根据文件建造一个示意图。",
                "",
                "用法：",
                "> build <文件名> - 加载并建造 '<文件名>.schematic'",
                "> build <文件名> <x> <y> <z> - 自定义位置"
        );
    }
}
