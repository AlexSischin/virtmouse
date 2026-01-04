import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.geom.Point2D;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class VirtualMouse {

    private final Random random;
    private final Robot robot;

    public VirtualMouse() throws AWTException {
        this.random = new Random(System.currentTimeMillis());
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        this.robot = new Robot(pointerInfo.getDevice());
    }

    /**
     * See the {@link VirtualMouse#getCliUsageMessage() getCliUsageMessage} method implementation for details.
     */
    public static void main(String[] args) {
        try {
            Config cfg = parse(args);
            run(cfg);
            System.exit(0);
        } catch (UsageException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            System.err.print(getCliUsageMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("runtime error");
            e.printStackTrace(System.err);
            System.exit(2);
        }
    }

    /**
     * Parses args.
     *
     * @param args to parse.
     * @return launch params.
     */
    private static Config parse(String[] args) {
        Objects.requireNonNull(args, "args");
        Config cfg = new Config();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];

            switch (a) {
                case "-h", "--help" -> cfg.showHelp = true;

                case "--min-delay-ms" -> {
                    if (i + 1 >= args.length) throw new UsageException("Missing value for --min-delay-ms.");
                    cfg.minDelayMs = parseNonNegativeInt(args[++i], "--min-delay-ms");
                }

                case "--max-delay-ms" -> {
                    if (i + 1 >= args.length) throw new UsageException("Missing value for --max-delay-ms.");
                    cfg.maxDelayMs = parseNonNegativeInt(args[++i], "--max-delay-ms");
                }

                case "--action" -> {
                    if (i + 1 >= args.length) throw new UsageException("Missing value for --action.");
                    String spec = args[++i];
                    cfg.actions.add(parseActionSpec(spec));
                }

                default -> throw new UsageException("Unknown argument: " + a);
            }
        }

        cfg.validate();
        return cfg;
    }

    /**
     * Parses non negative integer param value.
     *
     * @param s       param value string.
     * @param optName param name for error message if any.
     * @return int value.
     */
    private static int parseNonNegativeInt(String s, String optName) {
        int v = parseInt(s, optName);
        if (v < 0) throw new UsageException(optName + " must be >= 0.");
        return v;
    }

    /**
     * Parses integer param value.
     *
     * @param s       param value string.
     * @param optName param name for error message if any.
     * @return int value.
     */
    private static int parseInt(String s, String optName) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new UsageException("Invalid integer for " + optName + ": " + s);
        }
    }

    /**
     * Parses action param.
     *
     * @param spec action string.
     * @return parsed Action.
     */
    private static Action parseActionSpec(String spec) {
        if (spec == null || spec.isBlank()) throw new UsageException("Empty --action value.");

        // Spec is a single CLI argument; if user quoted it, it arrives intact (e.g., "move 200 100").
        String[] parts = spec.trim().split("\\s+");
        String verb = parts[0].toLowerCase();

        return switch (verb) {
            case "click" -> {
                if (parts.length != 1) throw new UsageException("Action 'click' takes no arguments.");
                yield new Click();
            }
            case "move" -> {
                if (parts.length != 3) throw new UsageException("Action 'move' requires 2 integers: move <x> <y>.");
                int x = parseInt(parts[1], "move <x>");
                int y = parseInt(parts[2], "move <y>");
                yield new Move(x, y);
            }
            default -> throw new UsageException("Unknown action: " + parts[0] + ". Supported: click, move <x> <y>.");
        };
    }

    /**
     * @return CLI usage message.
     */
    private static String getCliUsageMessage() {
        return """
                VirtualMouse - perform mouse actions sequentially with randomized delays
                
                Usage:
                  java -jar VirtualMouse.jar [options] --action <ACTION> [--action <ACTION> ...]
                
                Options:
                  --action <ACTION>        Add an action to execute. May be specified multiple times.
                                           ACTION formats:
                                             click
                                             move <x> <y>
                
                  --min-delay-ms <ms>      Minimum random delay between actions in milliseconds.
                                           Default: 50
                
                  --max-delay-ms <ms>      Maximum random delay between actions in milliseconds.
                                           Default: 200
                
                  -h, --help               Show this help and exit.
                
                Exit codes:
                  0  Success
                  1  Usage error (invalid arguments/options)
                  2  Runtime error
                
                Examples:
                  java -jar VirtualMouse.jar --action "move 1000 500" --action "click"
                """;
    }

    /**
     * Run the command.
     *
     * @param cfg params.
     */
    private static void run(Config cfg) throws AWTException, InterruptedException {
        if (cfg.showHelp) {
            System.out.print(getCliUsageMessage());
            return;
        }

        VirtualMouse mouse = new VirtualMouse();
        for (Action a : cfg.actions) {
            mouse.delay(cfg.minDelayMs, cfg.maxDelayMs);
            if (a instanceof Move(int x, int y)) {
                Point target = new Point(x, y);
                mouse.move(target);
            } else if (a instanceof Click) {
                mouse.click();
            }
        }
    }

    private void delay(int fromMs, int toMs) throws InterruptedException {
        Thread.sleep(random.nextInt(fromMs, toMs));
    }

    /**
     * Move cursor from current position to target position.
     *
     * @param target target position.
     */
    private void move(Point target) throws InterruptedException {
        Point start = MouseInfo.getPointerInfo().getLocation();
        List<TimedPoint> trajectory = calcTrajectory(start, target);

        for (TimedPoint tp : trajectory) {
            Point p = tp.point();
            robot.mouseMove(p.x, p.y);
            Thread.sleep(Duration.ofNanos(tp.delayNanos));
        }
    }

    /**
     * Performs single click at current mouse position.
     */
    private void click() throws InterruptedException {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(random.nextInt(10, 30));
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    /**
     * Random 6-th order Bezier curve.
     *
     * @param start initial point.
     * @param end   target point.
     */
    private List<TimedPoint> calcTrajectory(
            Point start,
            Point end
    ) {
        if (start == null) throw new IllegalArgumentException("start must not be null");
        if (end == null) throw new IllegalArgumentException("end must not be null");

        double pixelsPerSegment = 10;
        int segments = (int) (start.distance(end) / pixelsPerSegment);

        double delayFrom = random.nextDouble(20000000, 30000000) / Math.sqrt(segments);
        double delayTo = delayFrom * 2;
        double delayJitterMax = 400000000;
        double delayJitterChance = 0.005;

        if (segments <= 1) {
            return List.of(new TimedPoint(end, (int) delayFrom));
        }

        double pointJitter = 1;
        List<Point2D.Double> points = generateCheckPoints(start, end);
        List<TimedPoint> out = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            double t = (i + 1) / (double) segments;
            Point2D.Double p = deCasteljau(points, t);
            Point2D.Double pj = new Point2D.Double(
                    p.x + getRandomWithinRange(pointJitter), p.y + getRandomWithinRange(pointJitter));

            Point ip = new Point((int) Math.round(pj.x), (int) Math.round(pj.y));

            double k = Math.pow(i / (double) segments, 3);
            double delayMs = delayFrom + (delayTo - delayFrom) * k;

            double delayJitteredMs = delayMs;
            if (random.nextDouble() < delayJitterChance) {
                delayJitteredMs = delayMs + delayJitterMax * random.nextDouble();
            }

            out.add(new TimedPoint(ip, (int) Math.max(0, delayJitteredMs)));
        }
        return out;
    }

    /**
     * Generates random Bezier points including start and end.
     *
     * @param start start point.
     * @param end   end point.
     * @return Bezier points.
     */
    private List<Point2D.Double> generateCheckPoints(
            Point start,
            Point end
    ) {
        double distance = start.distance(end);

        double mxc = (start.x + end.x) / (double) 2;
        double myc = (start.y + end.y) / (double) 2;
        double mj = distance / 2;
        double mx = mxc + mj * random.nextDouble(-1, 1);
        double my = myc + mj * random.nextDouble(-1, 1);

        double sj = distance * 0.05;
        double ej = distance * 0.5;

        return List.of(
                new Point2D.Double(start.x, start.y),
                new Point2D.Double(start.x + getRandomWithinRange(sj), start.y + getRandomWithinRange(sj)),
                new Point2D.Double(mx, my),
                new Point2D.Double(end.x + getRandomWithinRange(ej), end.y + getRandomWithinRange(ej)),
                new Point2D.Double(end.x + getRandomWithinRange(ej), end.y + getRandomWithinRange(ej)),
                new Point2D.Double(end.x, end.y)
        );
    }

    /**
     * Calculates any continuous point at 0 <= t <= points.size().
     *
     * @param points Bezier curve points.
     * @param t      point at the curve.
     * @return 2d point.
     */
    private static Point2D.Double deCasteljau(List<Point2D.Double> points, double t) {
        int n = points.size();
        List<Point2D.Double> tmp = new ArrayList<>(n);
        for (Point2D.Double p : points) {
            tmp.add(new Point2D.Double(p.x, p.y));
        }

        for (int r = 1; r < n; r++) {
            for (int i = 0; i < n - r; i++) {
                Point2D.Double a = tmp.get(i);
                Point2D.Double b = tmp.get(i + 1);
                tmp.set(i, lerp(a, b, t));
            }
        }
        return tmp.getFirst();
    }

    private static Point2D.Double lerp(Point2D.Double a, Point2D.Double b, double t) {
        return new Point2D.Double(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t
        );
    }

    private double getRandomWithinRange(double range) {
        return range * random.nextDouble(-1, 1);
    }

    private record TimedPoint(Point point, int delayNanos) {
        public TimedPoint {
            if (point == null) throw new IllegalArgumentException("point must not be null");
            if (delayNanos < 0) throw new IllegalArgumentException("delayNanos must be >= 0");
        }
    }

    private static final class Config {
        public final List<Action> actions = new ArrayList<>();
        public int minDelayMs = 50;
        public int maxDelayMs = 200;
        public boolean showHelp = false;

        void validate() {
            if (minDelayMs < 0) throw new UsageException("--min-delay-ms must be >= 0.");
            if (maxDelayMs < 0) throw new UsageException("--max-delay-ms must be >= 0.");
            if (maxDelayMs < minDelayMs) throw new UsageException("--max-delay-ms must be >= --min-delay-ms.");
            if (!showHelp && actions.isEmpty()) throw new UsageException("At least one --action is required.");
        }
    }

    private sealed interface Action permits Click, Move {
    }

    private record Click() implements Action {
        @Override
        public String toString() {
            return "click";
        }
    }

    private record Move(int x, int y) implements Action {

        @Override
        public String toString() {
            return "move " + x + " " + y;
        }
    }

    private static final class UsageException extends RuntimeException {
        public UsageException(String message) {
            super(message);
        }
    }
}
