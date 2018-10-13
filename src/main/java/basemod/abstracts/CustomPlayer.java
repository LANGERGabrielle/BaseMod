package basemod.abstracts;

import basemod.BaseMod;
import basemod.animations.AbstractAnimation;
import basemod.animations.G3DJAnimation;
import basemod.interfaces.ModelRenderSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.Prefs;
import com.megacrit.cardcrawl.helpers.SaveHelper;
import com.megacrit.cardcrawl.localization.CharacterStrings;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.screens.CharSelectInfo;
import com.megacrit.cardcrawl.screens.stats.CharStat;
import com.megacrit.cardcrawl.screens.stats.StatsScreen;
import com.megacrit.cardcrawl.ui.panels.energyorb.EnergyOrbInterface;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Map;

public abstract class CustomPlayer extends AbstractPlayer implements ModelRenderSubscriber
{
	private static final Logger logger = LogManager.getLogger(CustomPlayer.class.getName());

	protected AbstractAnimation animation;

	protected EnergyOrbInterface energyOrb;
	protected Prefs prefs;
	protected CharStat charStat;

	public CustomPlayer(String name, PlayerClass playerClass, String[] orbTextures, String orbVfxPath,
			String model, String animation) {
		this(name, playerClass, orbTextures, orbVfxPath, null, model, animation);
	}
	
	public CustomPlayer(String name, PlayerClass playerClass, String[] orbTextures, String orbVfxPath, float[] layerSpeeds,
			String model, String animation) {
		this(name, playerClass, orbTextures, orbVfxPath, layerSpeeds, new G3DJAnimation(model, animation));
	}

	public CustomPlayer(String name, PlayerClass playerClass, String[] orbTextures, String orbVfxPath, AbstractAnimation animation) {
		this(name, playerClass, orbTextures, orbVfxPath, null, animation);
	}

	public CustomPlayer(String name, PlayerClass playerClass, String[] orbTextures, String orbVfxPath, float[] layerSpeeds,
						AbstractAnimation animation) {
		super(name, playerClass);

		energyOrb = new CustomEnergyOrb(orbTextures, orbVfxPath, layerSpeeds);
		charStat = new CharStat(this);
		
		this.dialogX = (this.drawX + 0.0F * Settings.scale);
		this.dialogY = (this.drawY + 220.0F * Settings.scale);

		this.animation = animation;

		if (animation.type() != AbstractAnimation.Type.NONE) {
			this.atlas = new TextureAtlas();
		}
		
		if (animation.type() == AbstractAnimation.Type.MODEL) {
			BaseMod.subscribe(this);
		}
	}

	@Override
	public void receiveModelRender(ModelBatch batch, Environment env) {
		if (this != AbstractDungeon.player) {
			BaseMod.unsubscribeLater(this);
		} else {
			animation.renderModel(batch, env);
		}
	}

	@Override
	public void renderPlayerImage(SpriteBatch sb) {
		switch (animation.type()) {
			case NONE:
				super.renderPlayerImage(sb);
				break;
			case MODEL:
				BaseMod.publishAnimationRender(sb);
				break;
			case SPRITE:
				animation.renderSprite(sb);
				break;
		}
	}

	@Override
	public String getAchievementKey()
	{
		// TODO
		return null;
	}

	@Override
	public ArrayList<AbstractCard> getCardPool(ArrayList<AbstractCard> tmpPool)
	{
		AbstractCard.CardColor color = BaseMod.getColor(chosenClass);
		for (Map.Entry<String, AbstractCard> c : CardLibrary.cards.entrySet()) {
			AbstractCard card = c.getValue();
			if (card.color.equals(color) && card.rarity != AbstractCard.CardRarity.BASIC &&
					(!UnlockTracker.isCardLocked(c.getKey()) || Settings.isDailyRun)) {
				tmpPool.add(card);
			}
		}
		return tmpPool;
	}

	@Override
	public String getLeaderboardCharacterName()
	{
		// This is never called
		// The one place it's called is gated behind an isModded check
		return null;
	}

	@Override
	public Texture getEnergyImage()
	{
		if (energyOrb instanceof CustomEnergyOrb) {
			return ((CustomEnergyOrb) energyOrb).getEnergyImage();
		}
		throw new RuntimeException();
	}

	@Override
	public void renderOrb(SpriteBatch sb, boolean enabled, float current_x, float current_y)
	{
		energyOrb.renderOrb(sb, enabled, current_x, current_x);
	}

	@Override
	public void updateOrb()
	{
		energyOrb.updateOrb();
	}

	@Override
	public String getSaveFilePath()
	{
		return SaveAndContinue.getPlayerSavePath(chosenClass);
	}

	@Override
	public Prefs getPrefs()
	{
		if (prefs == null) {
			logger.error("prefs need to be initialized first!");
		}
		return prefs;
	}

	@Override
	public void loadPrefs()
	{
		if (prefs == null) {
			prefs = SaveHelper.getPrefs(chosenClass.name());
		}
	}

	@Override
	public CharStat getCharStat()
	{
		return charStat;
	}

	@Override
	public int getUnlockedCardCount()
	{
		// TODO
		return 0;
	}

	@Override
	public int getSeenCardCount()
	{
		// TODO
		return 0;
	}

	@Override
	public int getCardCount()
	{
		// TODO
		return 0;
	}

	@Override
	public boolean saveFileExists()
	{
		return SaveAndContinue.saveExistsAndNotCorrupted(chosenClass);
	}

	@Override
	public String getWinStreakKey()
	{
		// The only places this is called then pass it to Steam integration
		// I'm uncertain what Steam will do with these unofficial keys
		return "win_streak_" + chosenClass.name();
	}

	@Override
	public String getLeaderboardWinStreakKey()
	{
		// This is never called
		// The one place it's called is gated behind an isModded check
		return chosenClass.name() + "_CONSECUTIVE_WINS";
	}

	@Override
	public void renderStatScreen(SpriteBatch sb, float screenX, float screenY)
	{
		StatsScreen.renderHeader(sb, BaseMod.colorString(getLocalizedCharacterName(), "#" + getCardColor().toString()), screenX, screenY);
		getCharStat().render(sb, screenX, screenY);
	}

	@Override
	public Texture getCustomModeCharacterButtonImage()
	{
		Pixmap pixmap = new Pixmap(Gdx.files.internal(BaseMod.getPlayerButton(chosenClass)));
		Pixmap small = new Pixmap(128, 128, pixmap.getFormat());
		small.drawPixmap(pixmap,
				0, 0, pixmap.getWidth(), pixmap.getHeight(),
				20, 20, small.getWidth()-40, small.getHeight()-40);
		Texture texture = new Texture(small);
		pixmap.dispose();
		small.dispose();
		return texture;
	}

	@Override
	public CharacterStrings getCharacterString()
	{
		CharSelectInfo loadout = getLoadout();
		CharacterStrings characterStrings = new CharacterStrings();
		characterStrings.NAMES = new String[]{loadout.name};
		characterStrings.TEXT = new String[]{loadout.flavorText};
		return characterStrings;
	}

	@Override
	public void refreshCharStat()
	{
		charStat = new CharStat(this);
	}
}
