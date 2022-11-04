package basemod.patches.com.megacrit.cardcrawl.vfx;

import basemod.Pair;
import com.badlogic.gdx.utils.Pool;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.scenes.TheBottomScene;
import com.megacrit.cardcrawl.scenes.TheCityScene;
import com.megacrit.cardcrawl.scenes.TheEndingScene;
import com.megacrit.cardcrawl.scenes.TitleBackground;
import com.megacrit.cardcrawl.ui.panels.DiscardPilePanel;
import com.megacrit.cardcrawl.ui.panels.DrawPilePanel;
import com.megacrit.cardcrawl.vfx.*;
import com.megacrit.cardcrawl.vfx.cardManip.CardGlowBorder;
import com.megacrit.cardcrawl.vfx.combat.BuffParticleEffect;
import com.megacrit.cardcrawl.vfx.combat.StunStarEffect;
import com.megacrit.cardcrawl.vfx.combat.UnknownParticleEffect;
import com.megacrit.cardcrawl.vfx.scene.*;
import javassist.*;
import javassist.bytecode.*;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class FixMemoryChurn
{
	private static final Class<?>[] handledVfx = new Class[] {
			// ui
			DiscardGlowEffect.class,
			GameDeckGlowEffect.class,
			CardGlowBorder.class,
			// intents
			DebuffParticleEffect.class,
			BuffParticleEffect.class,
			ShieldParticleEffect.class,
			UnknownParticleEffect.class,
			StunStarEffect.class,
			// torches
			TorchParticleLEffect.class,
			LightFlareLEffect.class,
			TorchParticleMEffect.class,
			LightFlareMEffect.class,
			TorchParticleSEffect.class,
			LightFlareSEffect.class,
			// main menu
			LogoFlameEffect.class,
			TitleDustEffect.class,
			// the bottom
			DustEffect.class,
			BottomFogEffect.class,
			// the city
			FireFlyEffect.class,
			FallingDustEffect.class,
			CeilingDustCloudEffect.class,
			// the beyond
			ShinySparkleEffect.class,
			WobblyCircleEffect.class,
	};
	private static final Pair<Class<?>, String>[] patches = new Pair[]{
			// ui
			new Pair(DiscardPilePanel.class, "updateVfx"),
			new Pair(DrawPilePanel.class, "updateVfx"),
			new Pair(AbstractCard.class, "updateGlow"),
			// intents
			new Pair(AbstractMonster.class, "updateIntentVFX"),
			// torches
			new Pair(InteractableTorchEffect.class, "update"),
			// main menu
			new Pair(TitleBackground.class, "updateFlame"),
			new Pair(TitleBackground.class, "updateDust"),
			// the bottom
			new Pair(TheBottomScene.class, "updateDust"),
			new Pair(TheBottomScene.class, "updateFog"),
			// the city
			new Pair(TheCityScene.class, "updateFireFlies"),
			new Pair(CeilingDustEffect.class, "update"),
			// the city
			new Pair(TheEndingScene.class, "updateParticles"),
	};

	private static class UnsafePool<T> extends Pool<T>
	{
		private static final Unsafe theUnsafe;

		static {
			try {
				Field f = Unsafe.class.getDeclaredField("theUnsafe");
				f.setAccessible(true);
				theUnsafe = (Unsafe) f.get(null);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				throw new RuntimeException("Failed to get Unsafe", e);
			}
		}

		private final Class<T> type;

		UnsafePool(Class<T> type)
		{
			this.type = type;
		}

		@Override
		protected T newObject()
		{
			try {
				return (T) theUnsafe.allocateInstance(type);
			} catch (InstantiationException e) {
				throw new RuntimeException(e);
			}
		}
	}

	// poolMap gets populated by a patch
	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static final Map<Class<?>, Pool<? extends AbstractGameEffect>> poolMap = new HashMap<>();

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void free(AbstractGameEffect vfx)
	{
		if (vfx.isDone) {
			Pool pool = poolMap.get(vfx.getClass());
			if (pool == null) {
				return;
			}
			pool.free(vfx);
		}
	}

	@SpirePatch2(
			clz = AbstractGameEffect.class,
			method = SpirePatch.CONSTRUCTOR
	)
	public static class SetupAndCreateAndFree
	{
		public static void Raw(CtBehavior ctBehavior) throws CannotCompileException, NotFoundException
		{
			ClassPool pool = ctBehavior.getDeclaringClass().getClassPool();
			CtClass ctChurn = pool.get(FixMemoryChurn.class.getName());
			CtConstructor sinit = ctChurn.getClassInitializer();

			int startLn = 42000;
			for (Class<?> cls : handledVfx) {
				CtClass ctClass = pool.get(cls.getName());

				CtMethod update = ctClass.getDeclaredMethod("update");
				update.insertAfter(FixMemoryChurn.class.getName() + ".free(this);");

				// Add pool_ to poolMap
				sinit.insertAfter(
						"poolMap.put(" + cls.getName() + ".class, new " + UnsafePool.class.getName() + "(" + cls.getName() + ".class));"
				);

				// Create setupSuper()
				CtMethod method = ctClass.getSuperclass().getDeclaredConstructor(new CtClass[]{}).toMethod("setupSuper", ctClass);
				ctClass.addMethod(method);

				// Create setup(...) and alloc_(...)
				int curLn = startLn;
				for (CtConstructor ctor : ctClass.getDeclaredConstructors()) {
					method = ctor.toMethod("setup", ctClass);
					if (ctor.callsSuper()) {
						method.insertBefore("setupSuper();");
					}

					ctClass.addMethod(method);
					method = CtNewMethod.make(
							Modifier.PUBLIC | Modifier.STATIC,
							ctClass, // return type
							"alloc_" + ctClass.getSimpleName(), // name
							ctor.getParameterTypes(), // param types
							null,
							"{" +
									ctClass.getName() + " ret = (" + ctClass.getName() + ") ((" + Pool.class.getName() + ") poolMap.get(" + ctClass.getName() + ".class)).obtain();" +
									"ret.setup($$);" +
									"return ret;" +
									"}",
							ctChurn
					);

					// This isn't necessary, but it's kinda cool and makes the alloc_ methods "more correct"
					ConstPool cp = method.getMethodInfo().getConstPool();
					// Mark as synthetic
					method.getMethodInfo().addAttribute(new SyntheticAttribute(cp));
					CodeAttribute code = method.getMethodInfo().getCodeAttribute();
					// Add line number info
					code.getAttributes().add(makeLineNumberAttribute(method.getMethodInfo().getConstPool(), curLn, code.getCodeLength()));
					// Local variable names
					LocalVariableAttribute oldLocals = (LocalVariableAttribute) ctor.getMethodInfo().getCodeAttribute().getAttribute(LocalVariableAttribute.tag);
					LocalVariableAttribute newLocals = new LocalVariableAttribute(cp);
					// Copy parameter names
					for (int i=0; i<oldLocals.tableLength(); ++i) {
						if (oldLocals.startPc(i) == 0 && !"this".equals(oldLocals.variableName(i))) { // is a parameter, but remove "this"
							newLocals.addEntry(0, code.getCodeLength(), cp.addUtf8Info(oldLocals.variableName(i)), cp.addUtf8Info(oldLocals.descriptor(i)), oldLocals.index(i)-1);
						}
					}
					int maxLocals = code.getMaxLocals();
					newLocals.addEntry(0, code.getCodeLength(), cp.addUtf8Info("ret"), cp.addUtf8Info(Descriptor.of(ctClass)), maxLocals-1);
					code.getAttributes().add(newLocals);

					ctChurn.addMethod(method);
					curLn += 10;
				}
				startLn += 100;
			}

			// Replace appropriate new calls with alloc_
			for (Pair<Class<?>, String> patch : patches) {
				CtClass ctClass = pool.get(patch.getKey().getName());
				CtMethod method = ctClass.getDeclaredMethod(patch.getValue());
				method.instrument(exprEditor);
			}
		}

		private static AttributeInfo makeLineNumberAttribute(ConstPool cp, int startLn, int codeLength)
		{
			final int[] lines = new int[] {0, 20, codeLength-2};
			byte[] data =new byte[(lines.length * 4) + 2];

			int idx = 0;
			ByteArray.write16bit(lines.length, data, 0);
			idx += 2;

			// for-each line
			for (int i=0; i<lines.length; ++i) {
				ByteArray.write16bit(lines[i], data, idx);
				idx += 2;
				ByteArray.write16bit(startLn + i, data, idx);
				idx += 2;
			}

			return new AttributeInfo(cp, LineNumberAttribute.tag, data);
		}

		private static final ExprEditor exprEditor = new ExprEditor() {
			@Override
			public void edit(NewExpr e) throws CannotCompileException
			{
				for (Class<?> cls : handledVfx) {
					if (e.getClassName().equals(cls.getName())) {
						e.replace("$_ = " + FixMemoryChurn.class.getName() + ".alloc_" + cls.getSimpleName() + "($$);");
						break;
					}
				}
			}
		};
	}
}
