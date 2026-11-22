package com.heysia.app.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {

    private fun parse(text: String) = CommandParser.parse(text)

    @Test fun opensApps() {
        assertEquals(Command.OpenApp("youtube"), parse("open youtube"))
        assertEquals(Command.OpenApp("camera"), parse("hey sia please open the camera app"))
        assertEquals(Command.OpenApp("play store"), parse("can you launch play store"))
        assertEquals(Command.OpenApp(null), parse("open"))
    }

    @Test fun callsPeople() {
        assertEquals(Command.Call("mom"), parse("call mom"))
        assertEquals(Command.Call("rafi"), parse("please call rafi on mobile"))
        assertEquals(Command.CallNumber("01712345678"), parse("dial zero one seven one two three four five six seven eight"))
        assertEquals(Command.CallNumber("0155"), parse("call zero one double five"))
        assertEquals(Command.Call(null), parse("make a call"))
    }

    @Test fun setsAlarms() {
        assertEquals(Command.SetAlarm(NumberWords.ClockTime(6, 0, false)), parse("set an alarm for six"))
        assertEquals(Command.SetAlarm(NumberWords.ClockTime(6, 30, true)), parse("wake me up at six thirty a m"))
        assertEquals(Command.SetAlarm(NumberWords.ClockTime(19, 15, true)), parse("set alarm for seven fifteen in the evening"))
        assertEquals(Command.SetAlarm(NumberWords.ClockTime(5, 45, false)), parse("alarm at quarter to six"))
        assertEquals(Command.SetAlarm(null, 1200), parse("set an alarm in twenty minutes"))
        assertEquals(Command.SetAlarm(null), parse("set an alarm"))
    }

    @Test fun setsTimers() {
        assertEquals(Command.SetTimer(600), parse("set a timer for ten minutes"))
        assertEquals(Command.SetTimer(5400), parse("start a timer for an hour and a half"))
        assertEquals(Command.SetTimer(1800), parse("timer for half an hour"))
        assertEquals(Command.SetTimer(3900), parse("timer one hour five minutes"))
        assertEquals(Command.SetTimer(45), parse("countdown forty five seconds"))
        assertEquals(Command.SetTimer(null), parse("set a timer"))
    }

    @Test fun playsMusic() {
        assertEquals(Command.PlayMusic("believer", MusicSource.Any), parse("play believer"))
        assertEquals(Command.PlayMusic("shape of you", MusicSource.YouTube), parse("play shape of you on youtube"))
        assertEquals(Command.PlayMusic("tum hi ho", MusicSource.Local), parse("play the song tum hi ho from my phone"))
        assertEquals(Command.PlayMusic("arijit singh", MusicSource.Any), parse("play songs by arijit singh"))
        assertEquals(Command.PlayMusic(null, MusicSource.Any), parse("play some music"))
        assertEquals(Command.PlayMusic("believer", MusicSource.YouTube), parse("i want to listen to believer on vanced"))
    }

    @Test fun controlsPlayback() {
        assertEquals(Command.Media(MediaAction.Pause), parse("pause the music"))
        assertEquals(Command.Media(MediaAction.Pause), parse("stop the song"))
        assertEquals(Command.Media(MediaAction.Resume), parse("resume"))
        assertEquals(Command.Media(MediaAction.Next), parse("next song"))
        assertEquals(Command.Media(MediaAction.Previous), parse("previous track"))
        assertEquals(Command.Stop, parse("stop"))
    }

    @Test fun togglesSettings() {
        assertEquals(Command.Toggle(PhoneSetting.WiFi, true), parse("turn on wifi"))
        assertEquals(Command.Toggle(PhoneSetting.WiFi, false), parse("wi fi off"))
        assertEquals(Command.Toggle(PhoneSetting.Bluetooth, true), parse("enable bluetooth"))
        assertEquals(Command.Toggle(PhoneSetting.MobileData, true), parse("turn on data"))
        assertEquals(Command.Toggle(PhoneSetting.MobileData, false), parse("turn off mobile data"))
        assertEquals(Command.Toggle(PhoneSetting.Hotspot, true), parse("turn on the hotspot"))
        assertEquals(Command.Toggle(PhoneSetting.Airplane, true), parse("turn on airplane mode"))
    }

    @Test fun sendsMessages() {
        assertEquals(
            Command.SendMessage(MessageApp.WhatsApp, "rafi", "i'm on my way"),
            parse("send a whatsapp message to rafi saying i'm on my way")
        )
        assertEquals(
            Command.SendMessage(MessageApp.Messenger, "tanvir", "call me later"),
            parse("send a message to tanvir on messenger saying call me later")
        )
        assertEquals(
            Command.SendMessage(MessageApp.WhatsApp, "mom", "i will be late"),
            parse("tell mom on whatsapp that i will be late")
        )
        assertEquals(Command.SendMessage(MessageApp.WhatsApp, "rafi", null), parse("send whatsapp to rafi"))
        assertEquals(Command.SendMessage(null, "rafi", "hello"), parse("send a message to rafi saying hello"))
        assertEquals(Command.SendMessage(MessageApp.WhatsApp, null, null), parse("send a whatsapp message"))
        assertEquals(Command.OpenApp("whatsapp"), parse("open whatsapp"))
    }

    @Test fun readsNotifications() {
        assertEquals(Command.ReadNotifications, parse("read my notifications"))
        assertEquals(Command.ReadNotifications, parse("any new notifications"))
        assertEquals(Command.ReadNotifications, parse("do i have any messages"))
    }

    @Test fun controlsTheFlashlight() {
        assertEquals(Command.Flashlight(true), parse("turn on the flashlight"))
        assertEquals(Command.Flashlight(false), parse("torch off"))
        assertEquals(Command.Flashlight(false), parse("turn off the light"))
    }

    @Test fun controlsVolume() {
        assertEquals(Command.Volume(VolumeChange.Up), parse("volume up"))
        assertEquals(Command.Volume(VolumeChange.Down), parse("turn the volume down"))
        assertEquals(Command.Volume(VolumeChange.Up), parse("louder"))
        assertEquals(Command.Volume(VolumeChange.Max), parse("volume max"))
        assertEquals(Command.Volume(VolumeChange.Mute), parse("mute"))
        assertEquals(Command.Volume(VolumeChange.Set, 50), parse("set volume to five"))
        assertEquals(Command.Volume(VolumeChange.Set, 30), parse("volume thirty percent"))
    }

    @Test fun answersQuestions() {
        assertEquals(Command.TellTime, parse("what time is it"))
        assertEquals(Command.TellTime, parse("what's the time"))
        assertEquals(Command.TellDate, parse("what's the date today"))
        assertEquals(Command.TellDate, parse("what day is it"))
        assertEquals(Command.Battery, parse("how much battery is left"))
    }

    @Test fun smallTalk() {
        assertEquals(Command.Stop, parse("never mind"))
        assertEquals(Command.Thanks, parse("thank you"))
        assertEquals(Command.Greeting, parse("hello"))
        assertEquals(Command.Help, parse("what can you do"))
        assertEquals(Command.Unknown("sing a song about cats"), parse("sing a song about cats"))
    }
}
