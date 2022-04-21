package basemod.patches.com.megacrit.cardcrawl.helpers.TipHelper;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.TipHelper;

@SpirePatch2(
		clz = TipHelper.class,
		method = "renderTipBox"
)
public class HeaderlessTip
{
	private static boolean isHeaderless = false;

	public static void renderHeaderlessTip(float x, float y, String text)
	{
		isHeaderless = true;
		TipHelper.renderGenericTip(x, y, "", text);
	}

	@SuppressWarnings("unused")
	private static SpireReturn<Void> Prefix(float x, float y, SpriteBatch sb, String title, String description,
											float ___textHeight, float ___SHADOW_DIST_X, float ___SHADOW_DIST_Y,
											float ___BOX_W, float ___BOX_EDGE_H,
											float ___TEXT_OFFSET_X, float ___HEADER_OFFSET_Y,
											float ___BODY_TEXT_WIDTH, float ___TIP_DESC_LINE_SPACING,
											Color ___BASE_COLOR)
	{
		if (isHeaderless && title != null && title.length() == 0) {

			float h = ___textHeight;

			// shadow
			sb.setColor(Settings.TOP_PANEL_SHADOW_COLOR);
			sb.draw(ImageMaster.KEYWORD_TOP,  x + ___SHADOW_DIST_X, y - ___SHADOW_DIST_Y, ___BOX_W, ___BOX_EDGE_H);
			if (h > 0) {
				sb.draw(ImageMaster.KEYWORD_BODY, x + ___SHADOW_DIST_X, y - h - ___SHADOW_DIST_Y, ___BOX_W, h);
			}
			sb.draw(ImageMaster.KEYWORD_BOT,  x + ___SHADOW_DIST_X, y - h - ___BOX_EDGE_H -  ___SHADOW_DIST_Y, ___BOX_W, ___BOX_EDGE_H + Math.min(h, 0f));
			// normal
			sb.setColor(Color.WHITE);
			sb.draw(ImageMaster.KEYWORD_TOP,  x, y, ___BOX_W, ___BOX_EDGE_H);
			if (h > 0) {
				sb.draw(ImageMaster.KEYWORD_BODY, x, y - h, ___BOX_W, h);
			}
			sb.draw(ImageMaster.KEYWORD_BOT,  x, y - h - ___BOX_EDGE_H, ___BOX_W, ___BOX_EDGE_H);

			FontHelper.renderSmartText(
					sb,
					FontHelper.tipBodyFont,
					description,
					x + ___TEXT_OFFSET_X,
					y + ___HEADER_OFFSET_Y,
					___BODY_TEXT_WIDTH,
					___TIP_DESC_LINE_SPACING,
					___BASE_COLOR
			);

			isHeaderless = false;
			return SpireReturn.Return();
		}

		isHeaderless = false;
		return SpireReturn.Continue();
	}
}
