package personal.thornupple.proximity;

import android.media.AudioManager;
import android.media.ToneGenerator;


// this class will process and report on the proximity readings
public class ProximityDataClass
{
	// must reverse the bytes, dang endians
	private static final byte LEFT = 2;
	private static final byte CENTER = 1;
	private static final byte RIGHT = 0;

	private static final int MAX_DISTANCE_CM = 60;
	private static final int MIN_DISTANCE_CM = 10;

	private int onDuration = 50;
	private int DTMF_TONE_FOR_SENSOR = 0;

	private Thread toneThread;

	// TODO: put in UI the ability to turn tone up or down
	private final ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

	// called from the main activity when we get new data from bluetooth
	public void PlayTone(byte[] proxData)
	{
		stopToneThread();

		int closestIndex;

		if (proxData[LEFT] <= proxData[CENTER] && proxData[LEFT] <= proxData[RIGHT])
		{
			closestIndex = LEFT;
		}
		else if (proxData[CENTER] <= proxData[LEFT] && proxData[CENTER] <= proxData[RIGHT])
		{
			closestIndex = CENTER;
		}
		else
		{
			closestIndex = RIGHT;
		}

		switch (closestIndex)
		{
			case LEFT:
				DTMF_TONE_FOR_SENSOR = ToneGenerator.TONE_DTMF_0;
				break;
			case CENTER:
				DTMF_TONE_FOR_SENSOR = ToneGenerator.TONE_DTMF_1;
				break;
			case RIGHT:
				DTMF_TONE_FOR_SENSOR = ToneGenerator.TONE_DTMF_3;
				break;
		}

		int distance = proxData[closestIndex];

		if (distance >= MAX_DISTANCE_CM)
		{
			return;
		}
		setOnOffDurations(distance);

		startToneThread();
		}

		private void setOnOffDurations ( int distance)
		{
			// distance from high to low that we want to split for on/off
			int reportSpread = MAX_DISTANCE_CM - MIN_DISTANCE_CM;

			// 100 ms below the frequency of the proximity sensor sending
			int totalDuration = 750;
			int segmentDuration = totalDuration / reportSpread;

			onDuration = segmentDuration * (MAX_DISTANCE_CM - distance);
		}

		private void startToneThread ()
		{
			if (onDuration == 0)
			{
				stopToneThread();
				return;
			}
			if (toneThread == null)
			{
				toneThread = new Thread(() -> {
					// plays solid tone until we sleep
					if (onDuration > 0 && !toneThread.isInterrupted())
						toneGenerator.startTone(DTMF_TONE_FOR_SENSOR,onDuration);
				});
				toneThread.start();
			}
		}

		public void stopToneThread ()
		{
			onDuration = 0;
			if (toneThread != null)
			{
				toneThread.interrupt();
				toneThread = null;
				toneGenerator.stopTone();
			}
		}
	}

/*
Requirements for notifying user:
Up to 3 sensors - all reading and reporting every half second and picked up here by the bluetooth broadcast receiver

Reporting will be by 3 different dtmf tones:  todo: see if the tones are adequate to report

starting tolerances:  start the tones if less than 30 CMs (about a foot).
solid tone at 5 CM or less
so increasing pulse rate as the distance decreases between 30CM to 5CM

 */