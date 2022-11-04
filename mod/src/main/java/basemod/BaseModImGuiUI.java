package basemod;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import imgui.ImGui;
import imgui.ImGuiTextFilter;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class BaseModImGuiUI
{
	private boolean firstTime = true;
	private final ImBoolean SHOW_DEMO_WINDOW = new ImBoolean(false);
	private ImBoolean showSearch = null;
	private ImBoolean showActionQueue = null;
	private final ImInt iData = new ImInt();

	void receiveImGui() {
		if (showSearch == null) {
			showSearch = new ImBoolean(BaseMod.getBoolean("imgui-search"));
			showActionQueue = new ImBoolean(BaseMod.getBoolean("imgui-actionqueue"));
		}
		boolean prevShowSearch = showSearch.get();
		boolean prevShowActionQueue = showActionQueue.get();

		ImVec2 wPos = ImGui.getMainViewport().getPos();
		ImGui.setNextWindowPos(wPos.x + 7, wPos.y + 70, ImGuiCond.FirstUseEver);
		ImGui.setNextWindowSize(465, 465, ImGuiCond.FirstUseEver);
		if (ImGui.begin("BaseMod")) {
			ImGui.checkbox("Demo", SHOW_DEMO_WINDOW);
			ImGui.sameLine();
			ImGui.checkbox("Search", showSearch);
			ImGui.sameLine();
			ImGui.checkbox("Action Queue", showActionQueue);
			ImGui.separator();
			combatPanel();
		}
		ImGui.end();

		if (showSearch.get()) {
			searchWindow();
		}

		if (showActionQueue.get()) {
			actionQueueWindow();
		}

		if (firstTime) {
			firstTime = false;
			ImGui.setWindowFocus("BaseMod");
		}

		if (SHOW_DEMO_WINDOW.get()) {
			ImGui.showDemoWindow(SHOW_DEMO_WINDOW);
		}

		if (prevShowSearch != showSearch.get()) {
			BaseMod.setBoolean("imgui-search", showSearch.get());
		}
		if (prevShowActionQueue != showActionQueue.get()) {
			BaseMod.setBoolean("imgui-actionqueue", showActionQueue.get());
		}
	}

	private void combatPanel() {
		ImGui.beginDisabled(AbstractDungeon.player == null);
		if (AbstractDungeon.player == null) ImGui.setNextItemOpen(false);
		if (ImGui.collapsingHeader("Player")) {
			playerInfo(AbstractDungeon.player);
		}
		ImGui.endDisabled();

		ImGui.beginDisabled(!inCombat());
		if (!inCombat()) ImGui.setNextItemOpen(false);
		if (ImGui.collapsingHeader("Monsters")) {
			Boolean openAction = null;
			if (ImGui.button("Open All")) {
				openAction = true;
			}
			ImGui.sameLine();
			if (ImGui.button("Close All")) {
				openAction = false;
			}

			int i = 0;
			for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
				monsterInfo(i, m, openAction);
				++i;
			}
		}
		ImGui.endDisabled();
	}

	private void playerInfo(AbstractPlayer p) {
		// act 4 keys
		if (ImGui.checkbox("emerald", Settings.hasEmeraldKey)) {
			Settings.hasEmeraldKey = !Settings.hasEmeraldKey;
		}
		ImGui.sameLine();
		if (ImGui.checkbox("ruby", Settings.hasRubyKey)) {
			Settings.hasRubyKey = !Settings.hasRubyKey;
		}
		ImGui.sameLine();
		if (ImGui.checkbox("sapphire", Settings.hasSapphireKey)) {
			Settings.hasSapphireKey = !Settings.hasSapphireKey;
		}
		// gold
		iData.set(p.gold);
		ImGui.inputInt("Gold", iData, 1, 100);
		if (iData.get() < 0) {
			iData.set(0);
		}
		if (iData.get() != p.gold) {
			p.gold = iData.get();
			p.displayGold = p.gold;
		}
		// max hp
		iData.set(p.maxHealth);
		ImGui.inputInt("Max HP", iData, 1, 10);
		if (iData.get() < 1) {
			iData.set(1);
		}
		if (iData.get() != p.maxHealth) {
			p.maxHealth = iData.get();
			p.currentHealth = Integer.min(p.currentHealth, p.maxHealth);
			p.healthBarUpdatedEvent();
			ReflectionHacks.setPrivate(p, AbstractCreature.class, "healthBarAnimTimer", 0.2f);
		}
		// hp
		creatureHP(p);
		// block
		creatureBlock(p);
		// energy
		iData.set(EnergyPanel.totalCount);
		ImGui.inputInt("Energy", iData, 1, 10);
		if (iData.get() < 0) {
			iData.set(0);
		}
		if (iData.get() != EnergyPanel.totalCount) {
			EnergyPanel.totalCount = iData.get();
			p.hand.glowCheck();
		}
		ImGui.sameLine();
		if (ImGui.checkbox("Infinite", DevConsole.infiniteEnergy)) {
			DevConsole.infiniteEnergy = !DevConsole.infiniteEnergy;
			if (DevConsole.infiniteEnergy) {
				EnergyPanel.setEnergy(9999);
			}
		}
		// powers
		creaturePowers(p);
		//deck
		ArrayList<AbstractCard> cards = AbstractDungeon.player.masterDeck.group;
		if (ImGui.treeNode(String.format("Deck (%d)###deck", cards.size()))) {
			if (ImGui.beginTable("deck cards", 4, ImGuiTableFlags.BordersInnerH)) {
				ImGui.tableSetupColumn("index", ImGuiTableColumnFlags.WidthFixed);
				ImGui.tableSetupColumn("card name");
				ImGui.tableSetupColumn("upgrade", ImGuiTableColumnFlags.WidthFixed);
				ImGui.tableSetupColumn("discard", ImGuiTableColumnFlags.WidthFixed);

				for (int i=0; i<cards.size(); ++i) {
					AbstractCard card = cards.get(i);
					ImGui.tableNextRow();
					// index
					ImGui.tableSetColumnIndex(0);
					ImGui.text(Integer.toString(i+1));
					// name
					ImGui.tableSetColumnIndex(1);
					ImGui.text(card.name);
					// upgrade
					ImGui.tableSetColumnIndex(2);
					ImGui.beginDisabled(!card.canUpgrade());
					if (ImGui.button("Upgrade##upgrade" + i)) {
						card.upgrade();
					}
					ImGui.endDisabled();
					// remove
					ImGui.tableSetColumnIndex(3);
					if (ImGui.button("Remove##remove" + i)) {
						AbstractDungeon.player.masterDeck.removeCard(card);
					}
				}
				ImGui.endTable();
			}
			ImGui.treePop();
		}
		// hand
		cards = AbstractDungeon.player.hand.group;
		if (ImGui.treeNode(String.format("Hand (%d)###hand", cards.size()))) {
			if (ImGui.button("Draw Card")) {
				addToTop(new DrawCardAction(1));
			}
			ImGui.sameLine();
			if (ImGui.button("Discard All")) {
				addToTop(new DiscardAction(p, p, cards.size(), false));
			}
			if (ImGui.beginTable("hand cards", 4, ImGuiTableFlags.BordersInnerH)) {
				ImGui.tableSetupColumn("index", ImGuiTableColumnFlags.WidthFixed);
				ImGui.tableSetupColumn("card name");
				ImGui.tableSetupColumn("upgrade", ImGuiTableColumnFlags.WidthFixed);
				ImGui.tableSetupColumn("discard", ImGuiTableColumnFlags.WidthFixed);

				for (int i=0; i<cards.size(); ++i) {
					AbstractCard card = cards.get(i);
					ImGui.tableNextRow();
					// index
					ImGui.tableSetColumnIndex(0);
					ImGui.text(Integer.toString(i+1));
					// name
					ImGui.tableSetColumnIndex(1);
					ImGui.text(card.name);
					// upgrade
					ImGui.tableSetColumnIndex(2);
					ImGui.beginDisabled(!card.canUpgrade());
					if (ImGui.button("Upgrade##upgrade" + i)) {
						card.upgrade();
						card.superFlash();
						card.applyPowers();
					}
					ImGui.endDisabled();
					// discard
					ImGui.tableSetColumnIndex(3);
					if (ImGui.button("Discard##discard" + i)) {
						addToTop(new DiscardSpecificCardAction(card));
					}
				}
				ImGui.endTable();
			}
			ImGui.treePop();
		}
	}

	private void monsterInfo(int i, AbstractMonster c, Boolean openAction) {
		if (c == null || c.isDeadOrEscaped()) return;
		if (openAction != null) {
			ImGui.setNextItemOpen(openAction);
		}
		if (ImGui.treeNode(i, c.name)) {
			creatureHP(c);
			creatureBlock(c);
			creaturePowers(c);
			ImGui.treePop();
		}
	}

	private void creatureHP(AbstractCreature c) {
		iData.set(c.currentHealth);
		ImGui.sliderInt("HP", iData.getData(), 1, c.maxHealth);
		if (iData.get() != c.currentHealth) {
			c.currentHealth = iData.get();
			c.healthBarUpdatedEvent();
			ReflectionHacks.setPrivate(c, AbstractCreature.class, "healthBarAnimTimer", 0.2f);
		}
		if (c instanceof AbstractMonster) {
			ImGui.sameLine();
			if (ImGui.button("Kill")) {
				addToTop(new InstantKillAction(c));
			}
		}
	}

	private void creatureBlock(AbstractCreature c) {
		iData.set(c.currentBlock);
		ImGui.dragScalar("Block", ImGuiDataType.S32, iData, 0.25f, 0, 999, "%d", ImGuiSliderFlags.AlwaysClamp);
		if (iData.get() != c.currentBlock) {
			if (c.currentBlock <= 0) {
				ReflectionHacks.privateMethod(AbstractCreature.class, "gainBlockAnimation").invoke(c);
			}
			c.currentBlock = iData.get();
		}
	}

	private void creaturePowers(AbstractCreature c) {
		if (!c.powers.isEmpty() && ImGui.treeNode("Powers")) {
			if (ImGui.beginTable("powers", 3, ImGuiTableFlags.BordersInnerH)) {
				ImGui.tableSetupColumn("amount", ImGuiTableColumnFlags.WidthFixed);
				ImGui.tableSetupColumn("power name");
				ImGui.tableSetupColumn("remove", ImGuiTableColumnFlags.WidthFixed);

				int i = 0;
				for (AbstractPower p : c.powers) {
					ImGui.tableNextRow();
					// amount
					ImGui.tableSetColumnIndex(0);
					ImGui.text(Integer.toString(p.amount));
					// name
					ImGui.tableSetColumnIndex(1);
					ImGui.text(p.name);
					// remove
					ImGui.tableSetColumnIndex(2);
					if (ImGui.button("Remove##remove" + i)) {
						addToTop(new RemoveSpecificPowerAction(c, c, p));
					}
					++i;
				}
				ImGui.endTable();
			}
			ImGui.treePop();
		}
	}

	private void actionQueueWindow() {
		ImVec2 wPos = ImGui.getMainViewport().getPos();
		ImGui.setNextWindowPos(wPos.x + 1560, wPos.y + 160, ImGuiCond.FirstUseEver);
		ImGui.setNextWindowSize(340, 270, ImGuiCond.FirstUseEver);
		if (ImGui.begin("Action Queue", showActionQueue)) {
			if (AbstractDungeon.actionManager.actions.isEmpty()) {
				ImGui.text("Empty");
			}

			if (ImGui.beginTable("action queue", 3, ImGuiTableFlags.BordersInnerH)) {
				if (AbstractDungeon.actionManager.currentAction != null) {
					if (actionRow(-1, AbstractDungeon.actionManager.currentAction)) {
						AbstractDungeon.actionManager.currentAction.isDone = true;
					}
				}
				List<AbstractGameAction> remove = new ArrayList<>();
				int i = 0;
				for (AbstractGameAction action : AbstractDungeon.actionManager.actions) {
					if (actionRow(i, action)) {
						remove.add(action);
					}
					++i;
				}
				AbstractDungeon.actionManager.actions.removeAll(remove);
				ImGui.endTable();
			}
		}
		ImGui.end();
	}

	private boolean actionRow(int i, AbstractGameAction action) {
		ImGui.tableNextRow();
		// name
		ImGui.tableSetColumnIndex(0);
		ImGui.text(action.getClass().getSimpleName());
		// duration
		ImGui.tableSetColumnIndex(1);
		float duration = ReflectionHacks.getPrivate(action, AbstractGameAction.class, "duration");
		ImGui.text(Float.toString(duration));
		// stop
		ImGui.tableSetColumnIndex(2);
		return ImGui.button("Remove##remove" + i);
	}

	private void searchWindow() {
		ImVec2 wPos = ImGui.getMainViewport().getPos();
		ImGui.setNextWindowPos(wPos.x + 7, wPos.y + 550, ImGuiCond.FirstUseEver);
		ImGui.setNextWindowSize(465, 255, ImGuiCond.FirstUseEver);
		if (ImGui.begin("Search", showSearch)) {
			if (ImGui.beginTabBar("SearchTabBar")) {
				cardSearchTab();
				relicSearchTab();
				potionSearchTab();
				ImGui.endTabBar();
			}
		}
		ImGui.end();
	}

	private String modIDFilter = "##ALL";
	private final ImGuiTextFilter cardFilter = new ImGuiTextFilter();
	private String selectedCardId = null;
	private final ImInt cardCount = new ImInt(1);
	private final ImInt cardUpgrades = new ImInt(0);

	private void cardSearchTab() {
		if (ImGui.beginTabItem("Cards")) {
			ArrayList<AbstractCard> allCards = CardLibrary.getAllCards();
			List<String> modIDs = allCards.stream()
					.map(c -> getModID(c.cardID))
					.distinct()
					.collect(Collectors.toList());
			modIDs.add(0, "##ALL");
			// modid
			ImGui.pushItemWidth(90);
			if (ImGui.beginCombo("##modid", modIDFilter)) {
				for (String modID : modIDs) {
					boolean isSelected = modIDFilter.equals(modID);
					if (ImGui.selectable(modID, isSelected)) {
						modIDFilter = modID;
					}

					if (isSelected) {
						ImGui.setItemDefaultFocus();
					}
				}
				ImGui.endCombo();
			}
			ImGui.popItemWidth();
			ImGui.sameLine();
			// filter
			ImGui.pushItemWidth(-1);
			cardFilter.draw("##");
			// card search
			float y = ImGui.getCursorPosY();
			float h = ImGui.getWindowHeight();
			float py = ImGui.getStyle().getWindowPaddingY();
			float lh = ImGui.getFrameHeightWithSpacing();
			if (ImGui.beginListBox("##all cards", -Float.MIN_VALUE, h - y - py - lh*2)) {
				ImVec2 textSize = new ImVec2();
				for (AbstractCard card : allCards) {
					if ((cardFilter.passFilter(card.name) || cardFilter.passFilter(card.cardID)) && ("##ALL".equals(modIDFilter) || getModID(card.cardID).equals(modIDFilter))) {
						boolean isSelected = selectedCardId != null && selectedCardId.equals(card.cardID);
						if (ImGui.selectable(String.format("%s###%s", card.name, card.cardID), isSelected)) {
							selectedCardId = card.cardID;
						}
						if (!Objects.equals(card.name, card.cardID)) {
							String text = String.format("id: %s", card.cardID);
							ImGui.calcTextSize(textSize, text);
							ImGui.sameLine(ImGui.getWindowContentRegionMaxX() - textSize.x);
							ImGui.text(text);
						}

						if (isSelected) {
							ImGui.setItemDefaultFocus();
						}
					}
				}
				ImGui.endListBox();
			}
			// card count
			ImGui.pushItemWidth(90);
			ImGui.inputInt("Count", cardCount);
			if (cardCount.get() < 1) {
				cardCount.set(1);
			}
			ImGui.sameLine();
			// upgrades
			ImGui.inputInt("Upgrades", cardUpgrades);
			ImGui.popItemWidth();
			ImGui.beginDisabled(selectedCardId == null || !inCombat());
			// add buttons
			if (ImGui.button("Add to hand")) {
				addToTop(new MakeTempCardInHandAction(getCardAndUpgrade(selectedCardId, cardUpgrades.get()), cardCount.get(), true));
			}
			ImGui.sameLine();
			ImGui.endDisabled();
			ImGui.beginDisabled(selectedCardId == null || AbstractDungeon.player == null);
			if (ImGui.button("Add to deck")) {
				UnlockTracker.markCardAsSeen(selectedCardId);
				for (int i=0; i<cardCount.get(); ++i){
					AbstractDungeon.effectList.add(
							new ShowCardAndObtainEffect(
									getCardAndUpgrade(selectedCardId, cardUpgrades.get()),
									Settings.WIDTH / 2.0f,
									Settings.HEIGHT / 2.0f
							)
					);
				}
			}
			ImGui.endDisabled();
			ImGui.endTabItem();
		}
	}

	private final ImGuiTextFilter relicFilter = new ImGuiTextFilter();
	private String selectedRelicId = null;

	private void relicSearchTab() {
		if (ImGui.beginTabItem("Relics")) {
			List<String> allRelicIDs = BaseMod.listAllRelicIDs();
			List<String> modIDs = allRelicIDs.stream()
					.map(this::getModID)
					.distinct()
					.collect(Collectors.toList());
			modIDs.add(0, "##ALL");
			// modid
			ImGui.pushItemWidth(90);
			if (ImGui.beginCombo("##modid", modIDFilter)) {
				for (String modID : modIDs) {
					boolean isSelected = modIDFilter.equals(modID);
					if (ImGui.selectable(modID, isSelected)) {
						modIDFilter = modID;
					}

					if (isSelected) {
						ImGui.setItemDefaultFocus();
					}
				}
				ImGui.endCombo();
			}
			ImGui.popItemWidth();
			ImGui.sameLine();
			// filter
			ImGui.pushItemWidth(-1);
			relicFilter.draw("##");
			// relic search
			float y = ImGui.getCursorPosY();
			float h = ImGui.getWindowHeight();
			float py = ImGui.getStyle().getWindowPaddingY();
			float lh = ImGui.getFrameHeightWithSpacing();
			if (ImGui.beginListBox("##all relics", -Float.MIN_VALUE, h - y - py - lh)) {
				ImVec2 textSize = new ImVec2();
				for (String relicID : allRelicIDs) {
					AbstractRelic relic = RelicLibrary.getRelic(relicID);
					if ((relicFilter.passFilter(relic.name) || relicFilter.passFilter(relic.relicId)) && ("##ALL".equals(modIDFilter) || getModID(relic.relicId).equals(modIDFilter))) {
						boolean isSelected = selectedRelicId != null && selectedRelicId.equals(relic.relicId);
						if (ImGui.selectable(String.format("%s###%s", relic.name, relic.relicId), isSelected)) {
							selectedRelicId = relic.relicId;
						}
						if (!Objects.equals(relic.name, relic.relicId)) {
							String text = String.format("id: %s", relic.relicId);
							ImGui.calcTextSize(textSize, text);
							ImGui.sameLine(ImGui.getWindowContentRegionMaxX() - textSize.x);
							ImGui.text(text);
						}

						if (isSelected) {
							ImGui.setItemDefaultFocus();
						}
					}
				}
				ImGui.endListBox();
			}
			// add button
			ImGui.beginDisabled(
					selectedRelicId == null || AbstractDungeon.player == null ||
							AbstractDungeon.currMapNode == null || AbstractDungeon.getCurrRoom() == null
			);
			if (ImGui.button("Obtain")) {
				AbstractRelic relic = RelicLibrary.getRelic(selectedRelicId).makeCopy();
				AbstractDungeon.getCurrRoom().spawnRelicAndObtain(
						Settings.WIDTH / 2f, Settings.HEIGHT / 2f,
						relic
				);
			}
			ImGui.endDisabled();
			ImGui.endTabItem();
		}
	}

	private final ImGuiTextFilter potionFilter = new ImGuiTextFilter();
	private String selectedPotionId = null;
	private List<AbstractPotion> allPotions = null;

	private void potionSearchTab() {
		if (ImGui.beginTabItem("Potions")) {
			List<String> allPotionIDs = PotionHelper.getPotions(AbstractPlayer.PlayerClass.IRONCLAD, true);
			if (allPotions == null) {
				allPotions = allPotionIDs.stream().map(PotionHelper::getPotion).collect(Collectors.toList());
			}
			List<String> modIDs = allPotionIDs.stream()
					.map(this::getModID)
					.distinct()
					.collect(Collectors.toList());
			modIDs.add(0, "##ALL");
			// modid
			ImGui.pushItemWidth(90);
			if (ImGui.beginCombo("##modid", modIDFilter)) {
				for (String modID : modIDs) {
					boolean isSelected = modIDFilter.equals(modID);
					if (ImGui.selectable(modID, isSelected)) {
						modIDFilter = modID;
					}

					if (isSelected) {
						ImGui.setItemDefaultFocus();
					}
				}
				ImGui.endCombo();
			}
			ImGui.popItemWidth();
			ImGui.sameLine();
			// filter
			ImGui.pushItemWidth(-1);
			potionFilter.draw("##");
			// relic search
			float y = ImGui.getCursorPosY();
			float h = ImGui.getWindowHeight();
			float py = ImGui.getStyle().getWindowPaddingY();
			float lh = ImGui.getFrameHeightWithSpacing();
			if (ImGui.beginListBox("##all potions", -Float.MIN_VALUE, h - y - py - lh)) {
				ImVec2 textSize = new ImVec2();
				for (AbstractPotion potion : allPotions) {
					if ((potionFilter.passFilter(potion.name) || potionFilter.passFilter(potion.ID)) && ("##ALL".equals(modIDFilter) || getModID(potion.ID).equals(modIDFilter))) {
						boolean isSelected = selectedPotionId != null && selectedPotionId.equals(potion.ID);
						if (ImGui.selectable(String.format("%s###%s", potion.name, potion.ID), isSelected)) {
							selectedPotionId = potion.ID;
						}
						if (!Objects.equals(potion.name, potion.ID)) {
							String text = String.format("id: %s", potion.ID);
							ImGui.calcTextSize(textSize, text);
							ImGui.sameLine(ImGui.getWindowContentRegionMaxX() - textSize.x);
							ImGui.text(text);
						}

						if (isSelected) {
							ImGui.setItemDefaultFocus();
						}
					}
				}
				ImGui.endListBox();
			}
			// add button
			ImGui.beginDisabled(selectedPotionId == null || AbstractDungeon.player == null);
			if (ImGui.button("Obtain")) {
				AbstractPotion potion = PotionHelper.getPotion(selectedPotionId);
				AbstractDungeon.player.obtainPotion(potion);
			}
			ImGui.endDisabled();
			ImGui.endTabItem();
		}
	}

	private String getModID(String cardID) {
		int idx = cardID.indexOf(':');
		if (idx < 0) return "Base game";
		return cardID.substring(0, idx);
	}

	private AbstractCard getCardAndUpgrade(String cardId, int upgrades) {
		AbstractCard ret = CardLibrary.getCard(cardId);
		if (ret != null) {
			ret = ret.makeCopy();
			for (int i=0; i<upgrades; ++i) {
				ret.upgrade();
			}
		}
		return ret;
	}

	private void addToTop(AbstractGameAction action) {
		AbstractDungeon.actionManager.addToTop(action);
	}

	private boolean inCombat() {
		return AbstractDungeon.currMapNode != null && AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
	}
}
