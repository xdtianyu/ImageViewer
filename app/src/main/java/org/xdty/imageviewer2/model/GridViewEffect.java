package org.xdty.imageviewer2.model;

import com.twotoasters.jazzylistview.JazzyEffect;
import com.twotoasters.jazzylistview.effects.CardsEffect;
import com.twotoasters.jazzylistview.effects.CurlEffect;
import com.twotoasters.jazzylistview.effects.FadeEffect;
import com.twotoasters.jazzylistview.effects.FanEffect;
import com.twotoasters.jazzylistview.effects.FlipEffect;
import com.twotoasters.jazzylistview.effects.FlyEffect;
import com.twotoasters.jazzylistview.effects.GrowEffect;
import com.twotoasters.jazzylistview.effects.HelixEffect;
import com.twotoasters.jazzylistview.effects.ReverseFlyEffect;
import com.twotoasters.jazzylistview.effects.SlideInEffect;
import com.twotoasters.jazzylistview.effects.StandardEffect;
import com.twotoasters.jazzylistview.effects.TiltEffect;
import com.twotoasters.jazzylistview.effects.TwirlEffect;
import com.twotoasters.jazzylistview.effects.WaveEffect;
import com.twotoasters.jazzylistview.effects.ZipperEffect;

/**
 * Created by ty on 15-5-12.
 */
public enum GridViewEffect {

    STANDARD,
    CARDS,
    CURL,
    FADE,
    FAN,
    FLIP,
    FLY,
    GROW,
    HELIX,
    REVERSE_FLY,
    SLIDE_IN,
    TILT,
    TWIRL,
    WAVE,
    ZIPPER;

    public static JazzyEffect build(GridViewEffect effect) {
        JazzyEffect jazzyEffect;
        switch (effect) {
            case CARDS:
                jazzyEffect =new CardsEffect();
                break;
            case CURL:
                jazzyEffect =new CurlEffect();
                break;
            case FADE:
                jazzyEffect =new FadeEffect();
                break;
            case FAN:
                jazzyEffect =new FanEffect();
                break;
            case FLIP:
                jazzyEffect =new FlipEffect();
                break;
            case FLY:
                jazzyEffect =new FlyEffect();
                break;
            case GROW:
                jazzyEffect =new GrowEffect();
                break;
            case HELIX:
                jazzyEffect =new HelixEffect();
                break;
            case REVERSE_FLY:
                jazzyEffect =new ReverseFlyEffect();
                break;
            case SLIDE_IN:
                jazzyEffect =new SlideInEffect();
                break;
            case STANDARD:
                jazzyEffect =new StandardEffect();
                break;
            case TILT:
                jazzyEffect =new TiltEffect();
                break;
            case TWIRL:
                jazzyEffect =new TwirlEffect();
                break;
            case WAVE:
                jazzyEffect =new WaveEffect();
                break;
            case ZIPPER:
                jazzyEffect =new ZipperEffect();
                break;
            default:
                jazzyEffect = new StandardEffect();
                break;
        }
        return jazzyEffect;
    }
}
