"""python3 -m unittest discover -s qa/scripts -p 'test_*.py'"""
import unittest

from framestats import summarize

HEADER = "Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,AnimationStart,PerformTraversalsStart,DrawStart,FrameDeadline,FrameInterval,FrameStartTime,SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted,"
MS = 1_000_000


def row(flags, start_ms, ui_ms, total_ms):
    s = start_ms * MS
    cells = [flags, 1, s, s, 0, s + MS, s + MS, s + 2 * MS, s + 3 * MS, s + 16 * MS, 16_666_667, s,
             s + MS + ui_ms * MS, s + MS + ui_ms * MS, s + (ui_ms + 2) * MS, s + total_ms * MS, s + total_ms * MS]
    return ",".join(str(c) for c in cells) + ","


def dump(*rows):
    return "\n".join(["Stats since: 1ns", "---PROFILEDATA---", HEADER, *rows, "---PROFILEDATA---", "View hierarchy:"])


class FramestatsTest(unittest.TestCase):
    # AC-U11: slow software rendering alone is not app jank; the UI thread's own time is what counts
    def test_slow_renderer_with_quick_ui_thread_is_not_janky(self):
        s = summarize(dump(*[row(0, i * 70, 6, 65) for i in range(10)]))
        self.assertEqual(s["uiFrames"], 10)
        self.assertEqual(s["uiJanky"], 0)
        self.assertEqual(s["uiJankPercent"], 0)

    def test_ui_thread_over_the_frame_interval_is_janky(self):
        s = summarize(dump(*[row(0, i * 70, 30 if i < 3 else 5, 60) for i in range(10)]))
        self.assertEqual(s["uiJanky"], 3)
        self.assertEqual(s["uiJankPercent"], 30)
        self.assertEqual(s["budgetMs"], 16.7)

    def test_flagged_frames_and_text_outside_the_block_are_ignored(self):
        s = summarize(dump(row(1, 0, 40, 60), row(0, 70, 4, 60)) + "\n1,2,3,4\n")
        self.assertEqual(s["uiFrames"], 1)
        self.assertEqual(s["uiJanky"], 0)

    def test_no_profile_data_reports_zero_frames(self):
        self.assertEqual(summarize("Total frames rendered: 0")["uiFrames"], 0)


if __name__ == "__main__":
    unittest.main()
