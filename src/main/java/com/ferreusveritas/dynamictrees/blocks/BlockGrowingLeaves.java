package com.ferreusveritas.dynamictrees.blocks;

import java.util.ArrayList;
import java.util.Random;

import com.ferreusveritas.dynamictrees.DynamicTrees;
import com.ferreusveritas.dynamictrees.TreeHelper;
import com.ferreusveritas.dynamictrees.items.Seed;
import com.ferreusveritas.dynamictrees.trees.DynamicTree;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityBlockDustFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockGrowingLeaves extends BlockLeaves implements ITreePart {
	
	private String[] species = {"X", "X", "X", "X"};
	private DynamicTree trees[] = new DynamicTree[4];
	
	public BlockGrowingLeaves() {
		field_150121_P = true;//True for alpha transparent leaves
	}
	
	public void setTree(int sub, DynamicTree tree) {
		trees[sub & 3] = tree;
		species[sub & 3] = tree.getName();
	}
	
	public DynamicTree getTree(int sub) {
		return trees[sub & 3];
	}

	@Override
	public DynamicTree getTree(IBlockAccess blockAccess, int x, int y, int z) {
		return getTree(getSubBlockNum(blockAccess, x, y, z));
	}
	
	//Borrow flammability from the vanilla minecraft leaves
	@Override
	public int getFlammability(IBlockAccess world, int x, int y, int z, ForgeDirection face) {
		return getTree(getSubBlockNum(world, x, y, z)).getPrimitiveLeaves().getBlock().getFlammability(world, x, y, z, face);
	}
	
	//Borrow fire spread rate from the vanilla minecraft leaves
	@Override
	public int getFireSpreadSpeed(IBlockAccess world, int x, int y, int z, ForgeDirection face) {
		return getTree(getSubBlockNum(world, x, y, z)).getPrimitiveLeaves().getBlock().getFireSpreadSpeed(world, x, y, z, face);
	}

	//Pull the subblock number portion from the metadata 
	public static final int getSubBlockNumFromMetadata(int meta){
		return (meta >> 2) & 3;
	}

	//Pull the subblock from the world
	public static int getSubBlockNum(IBlockAccess world, int x, int y, int z) {
		return getSubBlockNumFromMetadata(world.getBlockMetadata(x, y, z));
	}

	@Override
	public void updateTick(World world, int x, int y, int z, Random random) {
		//if(random.nextInt() % 4 == 0) {
			updateLeaves(world, x, y, z, random, true);
		//}
	}

	public void updateLeaves(World world, int x, int y, int z, Random random, boolean doBottomSpecials) {
		int metadata = world.getBlockMetadata(x, y, z);
		int sub = getSubBlockNumFromMetadata(metadata);

		DynamicTree tree = getTree(sub);
		int preHydro = getHydrationLevelFromMetadata(metadata);

		//Check hydration level.  Dry leaves are dead leaves.
		int hydro = getHydrationLevelFromNeighbors(world, x, y, z, tree);
		if(hydro == 0 || !hasAdequateLight(world, tree, x, y, z)){
			removeLeaves(world, x, y, z);//No water, no light .. no leaves
		} else { 
			//Encode new hydration level in metadata for this leaf
			if(preHydro != hydro) {//A little performance gain
				setHydrationLevel(world, x, y, z, hydro, metadata);
			}
		}

		for(ForgeDirection dir: ForgeDirection.VALID_DIRECTIONS) {//Go on all 6 sides of this block
			growLeaves(world, tree, x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);//Attempt to grow new leaves
		}

		//Do special things if the leaf block is/was on the bottom
		if(doBottomSpecials && isBottom(world, x, y, z)) {
			getTree(sub).bottomSpecial(world, x, y, z, random);
		}
	}

	@Override
	public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int metadata) {
		
		ForgeDirection dir = ForgeDirection.getOrientation(side).getOpposite();
		
		int dx = x + dir.offsetX;
		int dy = y + dir.offsetY;
		int dz = z + dir.offsetZ;

		DynamicTree tree = TreeHelper.getSafeTreePart(world, dx, dy, dz).getTree(world, dx, dy, dz);

		if(tree != null && tree.getGrowingLeaves() == this) {//Attempt to match the proper growing leaves for the tree being clicked on
			return tree.getGrowingLeavesSub() << 2;//Return matched metadata
		}

		return 0;
	}

	@Override
	public void breakBlock(World p_149749_1_, int p_149749_2_, int p_149749_3_, int p_149749_4_, Block p_149749_5_, int p_149749_6_){}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
		return AxisAlignedBB.getBoundingBox(x + 0.25, y, z + 0.25, x + 0.75, y + 0.50, z + 0.75);
	}

	@Override
	public void onFallenUpon(World world, int x, int y, int z, Entity entity, float fallDistance) {

		if(entity instanceof EntityLivingBase) { //We are only interested in Living things crashing through the canopy.
			entity.fallDistance--;

			int minX = MathHelper.floor_double(entity.boundingBox.minX + 0.001D);
			int minZ = MathHelper.floor_double(entity.boundingBox.minZ + 0.001D);
			int maxX = MathHelper.floor_double(entity.boundingBox.maxX - 0.001D);
			int maxZ = MathHelper.floor_double(entity.boundingBox.maxZ - 0.001D);

			boolean crushing = true;
			boolean hasLeaves = true;

			float volume = MathHelper.clamp_float(stepSound.getVolume() / 16.0f * fallDistance, 0, 3.0f);
			world.playSoundAtEntity(entity, this.stepSound.getBreakSound(), volume, this.stepSound.getPitch());

			for(int iy = 0; (entity.fallDistance > 3.0f) && crushing && ((y - iy) > 0); iy++) {
				if(hasLeaves) {//This layer has leaves that can help break our fall
					entity.fallDistance *= 0.66f;//For each layer we are crushing break the momentum
					hasLeaves = false;
				}
				for(int ix = minX; ix <= maxX; ix++) {
					for(int iz = minZ; iz <= maxZ; iz++) {
						if(TreeHelper.isLeaves(world, ix, y - iy, iz)) {
							hasLeaves = true;//This layer has leaves
							crushBlock(world, ix, y - iy, iz, entity);
						} else
						if (!world.isAirBlock(ix, y - iy, iz)) {
							crushing = false;//We hit something solid thus no longer crushing leaves layers
						}
					}
				}
			}
		}
	}

	public void crushBlock(World world, int x, int y, int z, Entity entity) {

		if(world.isRemote) {
			Random random = world.rand;
			int metadata = world.getBlockMetadata(x, y, z);
			for(int dz = 0; dz < 8; dz++) {
				for(int dy = 0; dy < 8; dy++) {
					for(int dx = 0; dx < 8; dx++) {
						if(MathHelper.getRandomIntegerInRange(random, 0, 8) == 0) {
							double fx = x + dx / 8.0;
							double fy = y + dy / 8.0;
							double fz = z + dz / 8.0;
							DynamicTrees.proxy.addDustParticle(world, fx, fy, fz, 0, MathHelper.randomFloatClamp(random, 0, (float) entity.motionY), 0, dx, dy, dz, this, metadata);
						}
					}
				}
			}
		}

		world.setBlockToAir(x, y, z);
	}

	@Override
	public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
		if (entity.motionY < 0.0D && entity.fallDistance < 2.0f) {
			entity.fallDistance = 0.0f;
			entity.motionY *= 0.5D;//Slowly sink into the block
		} else
		if (entity.motionY > 0 && entity.motionY < 0.25D) {
			entity.motionY += 0.025;//Allow a little climbing
		}

		entity.setSprinting(false);//One cannot sprint upon tree tops
		entity.motionX *= 0.25D;//Make travel slow and laborious
		entity.motionZ *= 0.25D;
	}

	@Override
	public void beginLeavesDecay(World world, int x, int y, int z) {}

	//Set the block at the provided coords to a leaf block if local light, space and hydration requirements are met
	public void growLeaves(World world, DynamicTree tree, int x, int y, int z){
		if(isLocationSuitableForNewLeaves(world, tree, x, y, z)){
			int hydro = getHydrationLevelFromNeighbors(world, x, y, z, tree);
			setBlockToLeaves(world, tree, x, y, z, hydro);
		}
	}

	//Set the block at the provided coords to a leaf block if local light and space requirements are met 
	public boolean growLeaves(World world, DynamicTree tree, int x, int y, int z, int hydro) {
		hydro = hydro == 0 ? tree.defaultHydration : hydro;
		if(isLocationSuitableForNewLeaves(world, tree, x, y, z)) {
			return setBlockToLeaves(world, tree, x, y, z, hydro);
		}
		return false;
	}

	//Test if the block at this location is capable of being grown into
	public boolean isLocationSuitableForNewLeaves(World world, DynamicTree tree, int x, int y, int z) {
		Block block = world.getBlock(x,  y,  z);
		
		if(block instanceof BlockGrowingLeaves) {
			return false;
		}

		Block belowBlock = world.getBlock(x, y - 1, z);

		//Prevent leaves from growing on the ground or above liquids
		if(belowBlock.isOpaqueCube() || belowBlock instanceof BlockLiquid) {
			return false;
		}

		//Help to grow into double tall grass and ferns in a more natural way
		if(block == Blocks.double_plant){
			int meta = world.getBlockMetadata(x, y, z);
			if((meta & 8) != 0) {//Top block of double plant 
				meta = world.getBlockMetadata(x, y - 1, z);
				if(meta == 2 || meta == 3) {//tall grass or fern
					world.setBlockToAir(x, y, z);
					world.setBlock(x, y - 1, z, Blocks.tallgrass, meta - 1, 3);
				}
			}
		}

		return block.isAir(world, x, y, z) && hasAdequateLight(world, tree, x, y, z);
	}

	/** Set the block at the provided coords to a leaf block and also set it's hydration value.
	* If hydration value is 0 then it sets the block to air
	*/
	public boolean setBlockToLeaves(World world, DynamicTree tree, int x, int y, int z, int hydro) {
		hydro = MathHelper.clamp_int(hydro, 0, 4);
		if(hydro != 0) {
			int sub = tree.getGrowingLeavesSub();
			world.setBlock(x, y, z, this, ((sub << 2) & 12) | ((hydro - 1) & 3), 2);//Removed Notify Neighbors Flag for performance
			return true;
		} else {
			removeLeaves(world, x, y, z);
			return false;
		}
	}

	/** Check to make sure the leaves have enough light to exist */
	public boolean hasAdequateLight(World world, DynamicTree tree, int x, int y, int z) {

		//If clear sky is above the block then we needn't go any further
		if(world.canBlockSeeTheSky(x, y, z)) {
			return true;
		}

		int smother = tree.smotherLeavesMax;

		//Check to make sure there isn't too many leaves above this block.  Encourages forest canopy development.
		if(smother != 0){
			if(isBottom(world, x, y, z, world.getBlock(x, y - 1, z))) {//Only act on the bottom block of the Growable stack
				//Prevent leaves from growing where they would be "smothered" from too much above foliage
				int smotherLeaves = 0;
				for(int i = 0; i < smother; i++) {
					smotherLeaves += TreeHelper.isTreePart(world, x, y + i + 1, z) ? 1 : 0;
				}
				if(smotherLeaves >= smother) {
					return false;
				}
			}
		}

		//Ensure the leaves don't grow in dark locations..  This creates a realistic canopy effect in forests and other nice stuff.
		//If there's already leaves here then don't kill them if it's a little dark
		//If it's empty space then don't create leaves unless it's sufficiently bright
		if(world.getSavedLightValue(EnumSkyBlock.Sky, x, y, z) >= (TreeHelper.isLeaves(world, x, y, z) ? 11 : 13)) {//TODO: Make ranges agile
			return true;
		}

		return false;
	}

	/** Used to find if the leaf block is at the bottom of the stack */
	public static boolean isBottom(World world, int x, int y, int z) {
		Block belowBlock = world.getBlock(x, y - 1, z);
		return isBottom(world, x, y, z, belowBlock);
	}

	/** Used to find if the leaf block is at the bottom of the stack */
	public static boolean isBottom(World world, int x, int y, int z, Block belowBlock) {
		if(TreeHelper.isTreePart(belowBlock)) {
			ITreePart belowTreepart = (ITreePart) belowBlock;
			return belowTreepart.getRadius(world, x, y - 1, z) > 1;//False for leaves, twigs, and dirt.  True for stocky branches
		}
		return true;//Non-Tree parts below indicate the bottom of stack
	}
	
	/** Gathers hydration levels from neighbors before pushing the values into the solver */
	public int getHydrationLevelFromNeighbors(IBlockAccess world, int x, int y, int z, DynamicTree tree) {

		int nv[] = new int[16];//neighbor hydration values

		for(ForgeDirection dir: ForgeDirection.VALID_DIRECTIONS) {
			int dx = x + dir.offsetX;
			int dy = y + dir.offsetY;
			int dz = z + dir.offsetZ;
			nv[TreeHelper.getSafeTreePart(world, dx, dy, dz).getHydrationLevel(world, dx, dy, dz, dir, tree)]++;
		}

		return solveCell(nv, tree.cellSolution);//Find center cell's value from neighbors  
	}

	/**
	* Cellular automata function that determines the behavior of the center cell from it's neighbors.
	* Values here are the number of neighbors for each hydration level.  Must be 16 elements.
	* Override member function to create unique species behavior
	*	4 Hex digits.. 0xXHCR  
	*	X: Reserved
	*	H: Selected hydration value
	*	C: Minimum count of neighbor blocks with selected hydration H
	*	R: Resulting Hydration
	*
	* Example:
	*	exampleSolver = 0x0514, 0x0413, 0x0312, 0x0211
	*	0x0514.. (5 X 1 = 4)  If there's 1 or more neighbor blocks with hydration 5 then make this block hydration 4
	* 
	* @param nv Array of counts of neighbor hydration values
	* @param solution Array of solver elements to solve the cell automata
	* @return
	*/
	public static int solveCell(int[] nv, short[] solution) {
		for(int d: solution) {
			if(nv[(d >> 8) & 15] >= ((d >> 4) & 15)) {
				return d & 15;
			}
		}
		return 0;
	}

	public int getHydrationLevelFromMetadata(int meta) {
		return (meta & 3) + 1;
	}

	public int getHydrationLevel(IBlockAccess blockAccess, int x, int y, int z) {
		return getHydrationLevelFromMetadata(blockAccess.getBlockMetadata(x, y, z));
	}

	/**
	* 0xODHR:Operation DirectionMask Hydrovalue Result
	*
	* Operations:
	*	0: return Result only
	*	1: return sum of Hydro and Result
	*	2: return diff of Hydro and Result
	*	3: return product of Hydro and Result
	*	4: return dividend of Hydro and Result
	* 
	*Directions bits:
	*	0x0100:Down
	*	0x0200:Up
	*	0x0400:Horizontal
	*
	*Hydrovalue:
	*	Any number you want to conditionally compare.  15(F) means any Hydrovalue
	*
	*Result:
	*	The number to return
	*
	*Example:
	*	solverData = { 0x02F0, 0x0144, 0x0742, 0x0132, 0x0730 } 
	*
	*	INIT ->	if dir or solution is undefined then return hydro
	*	02F0 -> else if dir is up(2) and hydro is equal to hydro(F) then return 0 (Always returns zero when direction is up)
	*	0144 -> else if dir is down(1) and hydro is equal to 4 then return 4
	*	0742 -> else if dir is any(7) and hydro is equal to 4 then return 2
	*	0132 -> else if dir is down(1) and hydro is equal to 3 then return 2
	*	0730 -> else if dir is any(7) and hydro is equal to 3 then return 0
	*	else return hydro
	*/
	@Override
	public int getHydrationLevel(IBlockAccess blockAccess, int x, int y, int z, ForgeDirection dir, DynamicTree leavesTree) {

		int metadata = blockAccess.getBlockMetadata(x, y, z);
		int hydro = getHydrationLevelFromMetadata(metadata);

		if(dir != null) {
			DynamicTree tree = getTree(getSubBlockNumFromMetadata(metadata));
			if(leavesTree != tree) {//Only allow hydration requests from the same type of leaves
				return 0;
			}
			short[] solution = tree.hydroSolution;
			if(solution != null) {
				int dirBits = dir == ForgeDirection.DOWN ? 0x100 : dir == ForgeDirection.UP ? 0x200 : 0x400;
				for(int d: solution) {
					if((d & dirBits) != 0) {
						int hydroCond = (d >> 4) & 15;
						hydroCond = hydroCond == 15 ? hydro : hydroCond;//15 is special and means the actual hydro value
						int result = d & 15;
						result = result == 15 ? hydro : result;
						if(hydro == hydroCond) {
							int op = (d >> 12) & 15;
							switch(op) {
							case 0: break;
							case 1: result = hydro + result; break;
							case 2: result = hydro - result; break;
							case 3: result = hydro * result; break;
							case 4: result = hydro / result; break;
							default: break;
							}
							return MathHelper.clamp_int(result, 0, 4);
						}
					}
				}
			}
		}

		return hydro;
	}

	public static void removeLeaves(World world, int x, int y, int z) {
		world.setBlockToAir(x, y, z);
		world.notifyBlocksOfNeighborChange(x, y, z, Blocks.air);
	}
	
	//Variable hydration levels are only appropriate for leaf blocks
	public static void setHydrationLevel(World world, int x, int y, int z, int hydro, int currMeta) {
		hydro = MathHelper.clamp_int(hydro, 0, 4);
		
		if(hydro == 0) {
			removeLeaves(world, x, y, z);
		} else {
			world.setBlockMetadataWithNotify(x, y, z, (currMeta & 12) | ((hydro - 1) & 3), 4);//TODO: Verify! Changed flags from 6 to 4
		}
	}
	
	@Override
	public GrowSignal growSignal(World world, int x, int y, int z, GrowSignal signal) {
		if(signal.step()) {//This is always placed at the beginning of every growSignal function
			branchOut(world, x, y, z, signal);//When a growth signal hits a leaf block it attempts to become a tree branch
		}
		return signal;
	}

	/**
	* Will place a leaves block if the position is air.
	* Otherwise it will check to see if the block is already there.
	* 
	* @param world
	* @param x
	* @param y
	* @param z
	* @param tree
	* @return True if the leaves are now at the coordinates.
	*/
	public boolean needLeaves(World world, int x, int y, int z, DynamicTree tree) {
		if(world.isAirBlock(x, y, z)){//Place Leaves if Air
			return this.growLeaves(world, tree, x, y, z, tree.defaultHydration);
		} else {//Otherwise check if there's already this type of leaves there.
			ITreePart treepart = TreeHelper.getSafeTreePart(world, x, y, z);
			return treepart == this && tree.getGrowingLeavesSub() == getSubBlockNum(world, x, y, z);//Check if this is the same type of leaves
		}
	}

	public GrowSignal branchOut(World world, int x, int y, int z, GrowSignal signal) {

		DynamicTree tree = signal.getTree();

		//Check to be sure the placement for a branch is valid by testing to see if it would first support a leaves block
		if(!needLeaves(world, x, y, z, tree)){
			signal.success = false;
			return signal;
		}

		//Check to see if there's neighboring branches and abort if there's any found.
		ForgeDirection originDir = signal.dir.getOpposite();

		for(ForgeDirection dir: ForgeDirection.VALID_DIRECTIONS) {
			if(!dir.equals(originDir)) {
				if(TreeHelper.isBranch(world, x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ)) {
					signal.success = false;
					return signal;
				}
			}
		}

		boolean hasLeaves = false;

		for(ForgeDirection dir: ForgeDirection.VALID_DIRECTIONS) {
			if(needLeaves(world, x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ, tree)) {
				hasLeaves = true;
			}
		}

		if(hasLeaves) {
			//Finally set the leaves block to a branch
			world.setBlock(x, y, z, signal.branchBlock, 0, 2);
			signal.radius = signal.getTree().secondaryThickness;//For the benefit of the parent branch
		}

		signal.success = hasLeaves;

		return signal;
	}

	@Override
	public int probabilityForBlock(IBlockAccess blockAccess, int x, int y, int z, BlockBranch from) {
		return from.getTree().isCompatibleGrowingLeaves(blockAccess, x, y, z) ? 2: 0;
	}

	//////////////////////////////
	// DROPS FUNCTIONS
	//////////////////////////////
	
	@Override
	public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
		ArrayList<ItemStack> ret = new ArrayList<ItemStack>();
		int chance = this.func_150123_b(metadata);

		//Hokey fortune stuff here.
		if (fortune > 0) {
			chance -= 2 << fortune;
			if (chance < 10) { 
				chance = 10;
			}
		}

		//It's mostly for seeds.. mostly.
		//Ignores quantityDropped() for Vanilla consistency and fortune compatibility.
		if (world.rand.nextInt(chance) == 0) {
			ret.add(new ItemStack(getSeedDropped(metadata)));
		}

		//More fortune contrivances here.  Vanilla compatible returns.
		chance = 200; //1 in 200 chance of returning an "apple"
		if (fortune > 0) {
			chance -= 10 << fortune;
			if (chance < 40) {
				chance = 40;
			}
		}

		//Get species specific drops.. apples or cocoa for instance
		getTree(getSubBlockNumFromMetadata(metadata)).getDrops(world, x, y, z, chance, ret);

		return ret;
	}

	@Override
	protected boolean canSilkHarvest() {
		return false;
	}

	//Drop a seed when the player destroys the block
	public Seed getSeedDropped(int meta) {
		return getTree(getSubBlockNumFromMetadata(meta)).getSeed();
	}

	//1 in 64 chance to drop a seed on destruction.. This quantity is used when the tree is cut down and not for when the leaves are directly destroyed.
	public int quantitySeedDropped(Random random) {
		return random.nextInt(64) == 0 ? 1 : 0;
	}

	//Some mods are using the following 3 member functions to find what items to drop, I'm disabling this behavior here.  I'm looking at you FastLeafDecay mod. ;)
	@Override
	public Item getItemDropped(int meta, Random random, int fortune) {
		return null;
	}

	@Override
	public int quantityDropped(Random random) {
		return 0;
	}

	@Override
	public int damageDropped(int metadata) {
		return 0;
	}

	//When the leaves are sheared just return vanilla leaves for usability
	@Override
	public ArrayList<ItemStack> onSheared(ItemStack item, IBlockAccess world, int x, int y, int z, int fortune) {
		int sub = getSubBlockNum(world, x, y, z);
		ArrayList<ItemStack> ret = new ArrayList<ItemStack>();
		ret.add(getTree(sub).getPrimitiveLeaves().toItemStack());
		return ret;
	}

	//////////////////////////////
	// RENDERING FUNCTIONS
	//////////////////////////////

	@Override
	public int getRadiusForConnection(IBlockAccess blockAccess, int x, int y, int z, BlockBranch from, int fromRadius) {
		return fromRadius == 1 && from.getTree().isCompatibleGrowingLeaves(blockAccess, x, y, z) ? 1 : 0;
	}

	//Gets the icon from the primitive block(Retains compatibility with Resource Packs)
	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int metadata) {
		return getTree(getSubBlockNumFromMetadata(metadata)).getPrimitiveLeaves().getIcon(side);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister iconRegister) {
	}

	//Returns the color this block should be rendered. Used by leaves.
	@Override
	@SideOnly(Side.CLIENT)
	public int getRenderColor(int metadata) {
		BlockAndMeta primLeaves = getTree(getSubBlockNumFromMetadata(metadata)).getPrimitiveLeaves();
		return primLeaves.getBlock().getRenderColor(primLeaves.getMeta());
	}

	//A hack to retain vanilla minecraft leaves block colors in their biomes
	@Override
	@SideOnly(Side.CLIENT)
	public int colorMultiplier(IBlockAccess access, int x, int y, int z) {
		//ugly hack for rendering saplings
		BlockBranch branch = TreeHelper.getBranch(access, x, y, z);
		int sub = branch != null ? branch.getTree().getGrowingLeavesSub() : getSubBlockNum(access, x, y, z);//Hacky for sapling renderer

		BlockAndMeta primLeaves = getTree(sub).getPrimitiveLeaves();
		if(primLeaves.matches(Blocks.leaves)){
			return
				(primLeaves.getMeta() & 3) == 1 ? ColorizerFoliage.getFoliageColorPine() : 
				(primLeaves.getMeta() & 3) == 2 ? ColorizerFoliage.getFoliageColorBirch() : 
				super.colorMultiplier(access, x, y, z);//Oak or Jungle
		}

		return super.colorMultiplier(access, x, y, z);//Something else
	}

	/*	TODO: Particle effects. Future leaves dropping from trees and wisps and stuff. Client side only
	@Override
	public void randomDisplayTick(World world, int x, int y, int z, Random random){
		if(isBottom(world, x, y, z)){
			EntityFX leaf = new EntityParticleLeaf(world, x + 0.5d, y - 0.5d, z + 0.5d, 0, -0.2, 0);
			Minecraft.getMinecraft().effectRenderer.addEffect(leaf);
		}
	}
	*/

	@Override
	public boolean isFoliage(IBlockAccess world, int x, int y, int z) {
		return true;
	}

	@Override
	public int getRadius(IBlockAccess blockAccess, int x, int y, int z) {
		return 0;
	}

	@Override
	public MapSignal analyse(World world, int x, int y, int z, ForgeDirection fromDir, MapSignal signal) {
		return signal;//Shouldn't need to run analysis on leaf blocks
	}

	@Override
	public boolean isRootNode() {
		return false;
	}

	@Override
	public int branchSupport(IBlockAccess blockAccess, BlockBranch branch, int x, int y, int z, ForgeDirection dir,	int radius) {
		//Leaves are only support for "twigs"
		return radius == 1 && branch.getTree() == getTree(blockAccess, x, y, z) ? 0x01 : 0;
	}

	@Override
	public boolean applyItemSubstance(World world, int x, int y, int z, EntityPlayer player, ItemStack itemStack) {
		return false;//Nothing is applied to leaves
	}

	@Override
	public int getMobilityFlag() {
		return 2;
	}

	//Included for compatibility.  Doesn't really seem to be needed in the way I use it.
	@Override
	public String[] func_150125_e() {
		return species;
	}

}