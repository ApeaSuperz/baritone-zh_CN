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

import baritone.KeepName;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.EntityClassById;
import baritone.api.command.datatypes.IDatatypeFor;
import baritone.api.command.datatypes.NearbyPlayer;
import baritone.api.command.exception.CommandErrorMessageException;
import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FollowCommand extends Command {

    public FollowCommand(IBaritone baritone) {
        super(baritone, "follow");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        FollowGroup group;
        FollowList list;
        List<Entity> entities = new ArrayList<>();
        List<Class<? extends Entity>> classes = new ArrayList<>();
        if (args.hasExactlyOne()) {
            baritone.getFollowProcess().follow((group = args.getEnum(FollowGroup.class)).filter);
        } else {
            args.requireMin(2);
            group = null;
            list = args.getEnum(FollowList.class);
            while (args.hasAny()) {
                Object gotten = args.getDatatypeFor(list.datatype);
                if (gotten instanceof Class) {
                    //noinspection unchecked
                    classes.add((Class<? extends Entity>) gotten);
                } else if (gotten != null) {
                    entities.add((Entity) gotten);
                }
            }
            baritone.getFollowProcess().follow(
                    classes.isEmpty()
                            ? entities::contains
                            : e -> classes.stream().anyMatch(c -> c.isInstance(e))
            );
        }
        if (group != null) {
            logDirect(String.format("正在跟随所有 %s", group.name().toLowerCase(Locale.US)));
        } else {
            if (classes.isEmpty()) {
                if (entities.isEmpty()) throw new NoEntitiesException();
                logDirect("正在跟随以下实体：");
                entities.stream()
                        .map(Entity::toString)
                        .forEach(this::logDirect);
            } else {
                logDirect("正在跟随以下类型的实体：");
                classes.stream()
                        .map(EntityList::getKey)
                        .map(Objects::requireNonNull)
                        .map(ResourceLocation::toString)
                        .forEach(this::logDirect);
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .append(FollowGroup.class)
                    .append(FollowList.class)
                    .filterPrefix(args.getString())
                    .stream();
        } else {
            IDatatypeFor followType;
            try {
                followType = args.getEnum(FollowList.class).datatype;
            } catch (NullPointerException e) {
                return Stream.empty();
            }
            while (args.has(2)) {
                if (args.peekDatatypeOrNull(followType) == null) {
                    return Stream.empty();
                }
                args.get();
            }
            return args.tabCompleteDatatype(followType);
        }
    }

    @Override
    public String getShortDesc() {
        return "跟随实体";
    }

    @Override
    public List<String> getLongDesc() {
        // TODO: Translate for entities like 'skeleton' and 'horse'
        return Arrays.asList(
                "follow 命令告诉 Baritone 跟随某些类型的实体。",
                "",
                "用法：",
                "> follow entities - 跟随所有实体",
                "> follow entity <实体1> <实体2> <...> - 跟随某些实体（例如 'skeleton'、'horse' 等）",
                "> follow players - 跟随玩家",
                "> follow player <玩家名1> <玩家名2> <...> - 跟随某些玩家"
        );
    }

    @KeepName
    private enum FollowGroup {
        ENTITIES(EntityLiving.class::isInstance),
        PLAYERS(EntityPlayer.class::isInstance); /* ,
        FRIENDLY(entity -> entity.getAttackTarget() != HELPER.mc.player),
        HOSTILE(FRIENDLY.filter.negate()); */
        final Predicate<Entity> filter;

        FollowGroup(Predicate<Entity> filter) {
            this.filter = filter;
        }
    }

    @KeepName
    private enum FollowList {
        ENTITY(EntityClassById.INSTANCE),
        PLAYER(NearbyPlayer.INSTANCE);

        final IDatatypeFor datatype;

        FollowList(IDatatypeFor datatype) {
            this.datatype = datatype;
        }
    }

    public static class NoEntitiesException extends CommandErrorMessageException {

        protected NoEntitiesException() {
            super("范围内没有有效的实体！");
        }

    }
}
