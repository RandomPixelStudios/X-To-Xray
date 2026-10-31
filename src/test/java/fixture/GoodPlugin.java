package fixture;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.event.Subscribe;
import com.xtoxray.api.plugin.event.XrayToggleEvent;
import java.util.List;
import java.util.stream.Collectors;

public class GoodPlugin implements Plugin {
    public static int received;

    record Pair(String a, int b) {
    }

    enum Mode { ON, OFF }

    @Override
    public void onEnable(PluginContext context) {
        String joined = List.of("a", "b").stream().map(s -> s + new Pair(s, 1)).collect(Collectors.joining());
        context.getLogger().info(joined + Mode.ON + System.currentTimeMillis());
        context.getEventBus().register(this);
    }

    @Subscribe
    public void onToggle(XrayToggleEvent event) {
        received++;
    }
}
