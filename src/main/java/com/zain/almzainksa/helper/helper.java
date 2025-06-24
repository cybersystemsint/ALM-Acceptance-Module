package com.zain.almzainksa.helper;


import org.springframework.stereotype.Controller;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;


@Controller
public class helper {
    public static void logBatchFile(String log, boolean includeDate,String batchfilename){
        try{
            String baseloc = "/home/app/logs/ALM/BatchFiles/"+batchfilename;
            FileWriter f = new FileWriter(new File(baseloc),true);
            if(includeDate)
                f.write(DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now() ) + System.lineSeparator());
            f.write(log + System.lineSeparator());
            f.close();
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    private static void renameFile(){
        Date d = new Date();
        String fname = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now().minusDays(1));
        Path source = Paths.get("/home/app/logs/ALM/Subrack/Subrack.log");
        try{
            Files.move(source, source.resolveSibling("Subrack.log-"+ fname));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getKenyaDateTimeString() {
        String formattedDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
        return formattedDateTime;
    }

    public static void logToFile(String log, String status){
        try{
            String baseloc = "/home/app/logs/ALM/Subrack/Subrack.log";
            try {
                Path source = Paths.get("/home/app/logs/ALM/Subrack/Subrack.log");
                FileTime creationTime = (FileTime) Files.getAttribute(source, "creationTime");
                LocalDateTime convertedFileTime = LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault());
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date date1 = sdf.parse(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(convertedFileTime));
                Date date2 = sdf.parse(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now()));

                int result = date1.compareTo(date2);
                if(result!=0){
                    renameFile();
                }

            } catch (IOException ex) {
            }

            FileWriter f = new FileWriter(new File(baseloc),true);
            f.write(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now() ) +" : "+ status +" : "+ log + System.lineSeparator());
            f.close();
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

}

