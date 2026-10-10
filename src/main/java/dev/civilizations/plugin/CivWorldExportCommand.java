package dev.civilizations.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.civilizations.simulation.world.WorldArchive;
import java.util.concurrent.CompletableFuture;

final class CivWorldExportCommand extends AbstractAsyncCommand {
    private final RequiredArg<Integer> x=withRequiredArg("x","Start X",ArgTypes.INTEGER);
    private final RequiredArg<Integer> y=withRequiredArg("y","Start Y",ArgTypes.INTEGER);
    private final RequiredArg<Integer> z=withRequiredArg("z","Start Z",ArgTypes.INTEGER);
    private final RequiredArg<Integer> width=withRequiredArg("width","Width",ArgTypes.INTEGER);
    private final RequiredArg<Integer> height=withRequiredArg("height","Height",ArgTypes.INTEGER);
    private final RequiredArg<Integer> depth=withRequiredArg("depth","Depth",ArgTypes.INTEGER);

    CivWorldExportCommand() { super("worldexport","Export a loaded Hytale region as reusable source archive."); }

    @Override protected CompletableFuture<Void> executeAsync(CommandContext context) {
        var universe=Universe.get();
        var world=universe==null?null:universe.getDefaultWorld();
        if(world==null||!world.isAlive()){
            context.sendMessage(Message.raw("CIV_WORLD_EXPORT_ERROR no active world"));
            return CompletableFuture.completedFuture(null);
        }
        int sx=context.get(x),sy=context.get(y),sz=context.get(z);
        int w=context.get(width),h=context.get(height),d=context.get(depth);
        if(w<1||h<1||d<1||(long)w*h*d>250000) {
            context.sendMessage(Message.raw("CIV_WORLD_EXPORT_ERROR invalid dimensions"));
            return CompletableFuture.completedFuture(null);
        }
        var bounds=new WorldArchive.Bounds(sx,sy,sz,sx+w,sy+h,sz+d);
        return CompletableFuture.runAsync(()->{
            try {
                var file=CivWorldArchiveExport.exportLoaded(world,bounds);
                context.sendMessage(Message.raw("CIV_WORLD_EXPORT_OK "+file));
            }catch(Exception error){
                context.sendMessage(Message.raw("CIV_WORLD_EXPORT_ERROR "+error.getMessage()));
            }
        },world);
    }
}
