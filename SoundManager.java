import javax.sound.sampled.*;
import java.io.File;

public class SoundManager {

    public static void play(String relativePath) {
        try {
            File file = new File(relativePath);

            if (!file.exists()) {
                System.out.println("FILE NOT FOUND: " + file.getAbsolutePath());
                return;
            }

            AudioInputStream audio = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            clip.start();

        } catch (Exception e) {
            System.out.println("Sound error: " + e.getMessage());
        }
    }
}
