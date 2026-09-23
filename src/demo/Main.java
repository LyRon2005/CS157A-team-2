package demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Local-only classroom booking prototype. Java 17+. */
public class Main {
    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path STORE = ROOT.resolve("data/bookings.tsv");
    private static final List<String[]> bookings = new ArrayList<>();
    private static final Set<String> ROOMS = Set.of("ENG-101", "ENG-201", "ENG-301", "SCI-101", "SCI-201", "SCI-301", "CL-101", "CL-201", "CL-301", "MLK-101", "MLK-201", "MLK-301");

    public static void main(String[] args) throws Exception {
        if (!Files.isDirectory(ROOT.resolve("web"))) throw new IllegalStateException("Set Eclipse working directory to SJSUClassroomsDemo (folder containing web).");
        if (Files.exists(STORE)) for (String line : Files.readAllLines(STORE)) {
            String[] row = line.split("\t", -1);
            if (row.length == 6) bookings.add(row);
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/bookings", Main::api);
        server.createContext("/", Main::staticFile);
        server.start();
        System.out.println("SJSUClassrooms demo: http://localhost:" + port);
        System.out.println("Local demo only. Stop with Eclipse Terminate or Ctrl+C.");
    }

    private static synchronized void api(HttpExchange x) throws IOException {
        try {
            if (x.getRequestMethod().equals("GET")) {
                StringJoiner result = new StringJoiner(",", "[", "]");
                for (String[] b : bookings) result.add("{\"room\":" + quote(b[0]) + ",\"date\":" + quote(b[1]) + ",\"start\":" + quote(b[2]) + ",\"end\":" + quote(b[3]) + ",\"name\":" + quote(b[4]) + ",\"purpose\":" + quote(b[5]) + "}");
                send(x, 200, "application/json", result.toString()); return;
            }
            if (!x.getRequestMethod().equals("POST")) { send(x,405,"application/json","{\"error\":\"Method not allowed\"}"); return; }
            byte[] body = x.getRequestBody().readNBytes(8193);
            if (body.length > 8192) { send(x,413,"application/json","{\"error\":\"Request too large\"}"); return; }
            Map<String,String> f = new HashMap<>();
            for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] p = pair.split("=", 2);
                if(p.length == 2) f.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),URLDecoder.decode(p[1],StandardCharsets.UTF_8).trim());
            }
            String room=f.getOrDefault("room",""), date=f.getOrDefault("date",""), start=f.getOrDefault("start",""), end=f.getOrDefault("end",""), name=f.getOrDefault("name",""), purpose=f.getOrDefault("purpose","");
            LocalDate d=LocalDate.parse(date); LocalTime s=LocalTime.parse(start), e=LocalTime.parse(end);
            if (!ROOMS.contains(room) || !date.equals(d.toString()) || !start.matches("\\d{2}:\\d{2}") || !end.matches("\\d{2}:\\d{2}") || !s.isBefore(e) || s.isBefore(LocalTime.of(8,0)) || e.isAfter(LocalTime.of(20,0)) || name.isBlank() || purpose.isBlank() || name.length()>80 || purpose.length()>200 || (name+purpose).chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
            // Every demo room has a sample class 09:00-10:30 on weekdays.
            boolean conflict=d.getDayOfWeek().getValue()<=5 && s.isBefore(LocalTime.of(10,30)) && e.isAfter(LocalTime.of(9,0));
            for(String[] b:bookings) if(b[0].equals(room)&&b[1].equals(date)&&s.isBefore(LocalTime.parse(b[3]))&&e.isAfter(LocalTime.parse(b[2]))) conflict=true;
            if(conflict) { send(x,409,"application/json","{\"error\":\"This time overlaps a class or an existing booking.\"}"); return; }
            String[] row={room,date,start,end,name,purpose};
            Files.createDirectories(STORE.getParent());
            Files.writeString(STORE,String.join("\t",row)+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            bookings.add(row);
            send(x,201,"application/json","{\"ok\":true}");
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            send(x,400,"application/json","{\"error\":\"Check your details. Available hours: 08:00–20:00. The end time must be after the start time.\"}");
        } catch (IOException ex) {
            ex.printStackTrace(); send(x,500,"application/json","{\"error\":\"Could not save the booking. Please try again.\"}");
        }
    }

    private static void staticFile(HttpExchange x) throws IOException {
        if(!x.getRequestMethod().equals("GET")) { send(x,405,"text/plain","Method not allowed"); return; }
        String p=x.getRequestURI().getPath();
        if(p.equals("/")) p="/index.html";
        Path web=ROOT.resolve("web"); Path file=web.resolve(p.substring(1)).normalize();
        if(!file.startsWith(web)||!Files.isRegularFile(file)) { send(x,404,"text/plain","Not found"); return; }
        String type=p.endsWith(".js")?"text/javascript":p.endsWith(".css")?"text/css":p.endsWith(".geojson")?"application/geo+json":p.endsWith(".json")?"application/json":"text/html";
        send(x,200,type,Files.readString(file));
    }
    private static String quote(String s) { return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\""; }
    private static void send(HttpExchange x,int code,String type,String text) throws IOException {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.sendResponseHeaders(code,bytes.length);
        try(OutputStream out=x.getResponseBody()){out.write(bytes);}
    }
}
