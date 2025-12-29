/**
 * Meal Inspiration Service (Web Version)
 * Provides ingredient options for AI meal inspirations
 * Matches Android MealRecommendationViewModel.kt
 */

export enum IngredientCategory {
  PROTEIN = 'PROTEIN',
  FRUIT = 'FRUIT',
  VEGETABLE = 'VEGETABLE',
  HEALTHY_FAT = 'HEALTHY_FAT',
  GRAIN = 'GRAIN',
  PANTRY = 'PANTRY',
}

export interface IngredientOption {
  name: string
  category: IngredientCategory
}

export interface IngredientCategoryGroup {
  category: IngredientCategory
  title: string
  options: IngredientOption[]
}

class MealInspirationService {
  private static instance: MealInspirationService

  private constructor() {}

  static getInstance(): MealInspirationService {
    if (!MealInspirationService.instance) {
      MealInspirationService.instance = new MealInspirationService()
    }
    return MealInspirationService.instance
  }

  /**
   * Get all ingredient options grouped by category
   * Matches Android MealRecommendationViewModel.kt
   */
  getIngredientOptions(): IngredientCategoryGroup[] {
    return [
      // PROTEINS
      {
        category: IngredientCategory.PROTEIN,
        title: 'Proteins',
        options: [
          { name: 'Chicken Breast', category: IngredientCategory.PROTEIN },
          { name: 'Chicken Thighs', category: IngredientCategory.PROTEIN },
          { name: 'Ground Turkey', category: IngredientCategory.PROTEIN },
          { name: 'Ground Chicken', category: IngredientCategory.PROTEIN },
          { name: 'Lean Beef', category: IngredientCategory.PROTEIN },
          { name: 'Ground Beef', category: IngredientCategory.PROTEIN },
          { name: 'Ground Pork', category: IngredientCategory.PROTEIN },
          { name: 'Steak', category: IngredientCategory.PROTEIN },
          { name: 'Pork Tenderloin', category: IngredientCategory.PROTEIN },
          { name: 'Bacon', category: IngredientCategory.PROTEIN },
          { name: 'Ham', category: IngredientCategory.PROTEIN },
          { name: 'Salmon', category: IngredientCategory.PROTEIN },
          { name: 'Tuna', category: IngredientCategory.PROTEIN },
          { name: 'Cod', category: IngredientCategory.PROTEIN },
          { name: 'Pollock', category: IngredientCategory.PROTEIN },
          { name: 'Tilapia', category: IngredientCategory.PROTEIN },
          { name: 'Halibut', category: IngredientCategory.PROTEIN },
          { name: 'Mackerel', category: IngredientCategory.PROTEIN },
          { name: 'Sardines', category: IngredientCategory.PROTEIN },
          { name: 'Trout', category: IngredientCategory.PROTEIN },
          { name: 'Sea Bass', category: IngredientCategory.PROTEIN },
          { name: 'Mahi Mahi', category: IngredientCategory.PROTEIN },
          { name: 'Snapper', category: IngredientCategory.PROTEIN },
          { name: 'Swordfish', category: IngredientCategory.PROTEIN },
          { name: 'Haddock', category: IngredientCategory.PROTEIN },
          { name: 'Anchovies', category: IngredientCategory.PROTEIN },
          { name: 'Tuna Steak', category: IngredientCategory.PROTEIN },
          { name: 'Arctic Char', category: IngredientCategory.PROTEIN },
          { name: 'Perch', category: IngredientCategory.PROTEIN },
          { name: 'Grouper', category: IngredientCategory.PROTEIN },
          { name: 'Shrimp', category: IngredientCategory.PROTEIN },
          { name: 'Scallops', category: IngredientCategory.PROTEIN },
          { name: 'Crab', category: IngredientCategory.PROTEIN },
          { name: 'Lobster', category: IngredientCategory.PROTEIN },
          { name: 'Mussels', category: IngredientCategory.PROTEIN },
          { name: 'Oysters', category: IngredientCategory.PROTEIN },
          { name: 'Clams', category: IngredientCategory.PROTEIN },
          { name: 'Octopus', category: IngredientCategory.PROTEIN },
          { name: 'Squid', category: IngredientCategory.PROTEIN },
          { name: 'Flounder', category: IngredientCategory.PROTEIN },
          { name: 'Sole', category: IngredientCategory.PROTEIN },
          { name: 'Monkfish', category: IngredientCategory.PROTEIN },
          { name: 'Branzino', category: IngredientCategory.PROTEIN },
          { name: 'Barramundi', category: IngredientCategory.PROTEIN },
          { name: 'Rainbow Trout', category: IngredientCategory.PROTEIN },
          { name: 'Black Cod', category: IngredientCategory.PROTEIN },
          { name: 'Yellowtail', category: IngredientCategory.PROTEIN },
          { name: 'Crawfish', category: IngredientCategory.PROTEIN },
          { name: 'Langoustine', category: IngredientCategory.PROTEIN },
          { name: 'Red Snapper', category: IngredientCategory.PROTEIN },
          { name: 'Tofu', category: IngredientCategory.PROTEIN },
          { name: 'Tempeh', category: IngredientCategory.PROTEIN },
          { name: 'Eggs', category: IngredientCategory.PROTEIN },
          { name: 'Egg Whites', category: IngredientCategory.PROTEIN },
          { name: 'Black Beans', category: IngredientCategory.PROTEIN },
          { name: 'Kidney Beans', category: IngredientCategory.PROTEIN },
          { name: 'Chickpeas', category: IngredientCategory.PROTEIN },
          { name: 'Lentils', category: IngredientCategory.PROTEIN },
          { name: 'Edamame', category: IngredientCategory.PROTEIN },
          { name: 'Turkey Breast', category: IngredientCategory.PROTEIN },
          { name: 'Duck Breast', category: IngredientCategory.PROTEIN },
          { name: 'Lamb Chop', category: IngredientCategory.PROTEIN },
          { name: 'Ground Lamb', category: IngredientCategory.PROTEIN },
          { name: 'Pork Shoulder', category: IngredientCategory.PROTEIN },
          { name: 'Beef Brisket', category: IngredientCategory.PROTEIN },
          { name: 'Beef Short Ribs', category: IngredientCategory.PROTEIN },
          { name: 'Lamb Leg', category: IngredientCategory.PROTEIN },
          { name: 'Ground Bison', category: IngredientCategory.PROTEIN },
          { name: 'Elk', category: IngredientCategory.PROTEIN },
          { name: 'Rabbit', category: IngredientCategory.PROTEIN },
          { name: 'Oxtail', category: IngredientCategory.PROTEIN },
        ],
      },
      // FRUITS
      {
        category: IngredientCategory.FRUIT,
        title: 'Fruits',
        options: [
          { name: 'Banana', category: IngredientCategory.FRUIT },
          { name: 'Apple', category: IngredientCategory.FRUIT },
          { name: 'Blueberries', category: IngredientCategory.FRUIT },
          { name: 'Strawberries', category: IngredientCategory.FRUIT },
          { name: 'Raspberries', category: IngredientCategory.FRUIT },
          { name: 'Blackberries', category: IngredientCategory.FRUIT },
          { name: 'Avocado', category: IngredientCategory.FRUIT },
          { name: 'Pineapple', category: IngredientCategory.FRUIT },
          { name: 'Mango', category: IngredientCategory.FRUIT },
          { name: 'Orange', category: IngredientCategory.FRUIT },
          { name: 'Grapefruit', category: IngredientCategory.FRUIT },
          { name: 'Grapes', category: IngredientCategory.FRUIT },
          { name: 'Peach', category: IngredientCategory.FRUIT },
          { name: 'Pear', category: IngredientCategory.FRUIT },
          { name: 'Cherries', category: IngredientCategory.FRUIT },
          { name: 'Kiwi', category: IngredientCategory.FRUIT },
          { name: 'Watermelon', category: IngredientCategory.FRUIT },
          { name: 'Cantaloupe', category: IngredientCategory.FRUIT },
          { name: 'Lemon', category: IngredientCategory.FRUIT },
          { name: 'Lime', category: IngredientCategory.FRUIT },
        ],
      },
      // VEGETABLES
      {
        category: IngredientCategory.VEGETABLE,
        title: 'Vegetables',
        options: [
          { name: 'Spinach', category: IngredientCategory.VEGETABLE },
          { name: 'Kale', category: IngredientCategory.VEGETABLE },
          { name: 'Arugula', category: IngredientCategory.VEGETABLE },
          { name: 'Lettuce', category: IngredientCategory.VEGETABLE },
          { name: 'Broccoli', category: IngredientCategory.VEGETABLE },
          { name: 'Brussels Sprouts', category: IngredientCategory.VEGETABLE },
          { name: 'Bell Peppers', category: IngredientCategory.VEGETABLE },
          { name: 'Onion', category: IngredientCategory.VEGETABLE },
          { name: 'Red Onion', category: IngredientCategory.VEGETABLE },
          { name: 'Tomato', category: IngredientCategory.VEGETABLE },
          { name: 'Cherry Tomatoes', category: IngredientCategory.VEGETABLE },
          { name: 'Carrots', category: IngredientCategory.VEGETABLE },
          { name: 'Cauliflower', category: IngredientCategory.VEGETABLE },
          { name: 'Zucchini', category: IngredientCategory.VEGETABLE },
          { name: 'Yellow Squash', category: IngredientCategory.VEGETABLE },
          { name: 'Mushrooms', category: IngredientCategory.VEGETABLE },
          { name: 'Asparagus', category: IngredientCategory.VEGETABLE },
          { name: 'Green Beans', category: IngredientCategory.VEGETABLE },
          { name: 'Snap Peas', category: IngredientCategory.VEGETABLE },
          { name: 'Cucumber', category: IngredientCategory.VEGETABLE },
          { name: 'Celery', category: IngredientCategory.VEGETABLE },
          { name: 'Cabbage', category: IngredientCategory.VEGETABLE },
          { name: 'Eggplant', category: IngredientCategory.VEGETABLE },
          { name: 'Okra', category: IngredientCategory.VEGETABLE },
          { name: 'Radish', category: IngredientCategory.VEGETABLE },
          { name: 'Beets', category: IngredientCategory.VEGETABLE },
          { name: 'Corn', category: IngredientCategory.VEGETABLE },
          { name: 'Peas', category: IngredientCategory.VEGETABLE },
        ],
      },
      // NUTS AND SEEDS
      {
        category: IngredientCategory.HEALTHY_FAT,
        title: 'Nuts and Seeds',
        options: [
          { name: 'Almonds', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Walnuts', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Cashews', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Pistachios', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Pecans', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Macadamia Nuts', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Peanuts', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Hazelnuts', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Brazil Nuts', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Chia Seeds', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Flaxseed', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Hemp Seeds', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Pumpkin Seeds', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Sunflower Seeds', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Sesame Seeds', category: IngredientCategory.HEALTHY_FAT },
          { name: 'Pine Nuts', category: IngredientCategory.HEALTHY_FAT },
        ],
      },
      // DAIRY
      {
        category: IngredientCategory.PANTRY,
        title: 'Dairy',
        options: [
          { name: 'Greek Yogurt', category: IngredientCategory.PANTRY },
          { name: 'Cottage Cheese', category: IngredientCategory.PANTRY },
          { name: 'Milk', category: IngredientCategory.PANTRY },
          { name: 'Almond Milk', category: IngredientCategory.PANTRY },
          { name: 'Oat Milk', category: IngredientCategory.PANTRY },
          { name: 'Soy Milk', category: IngredientCategory.PANTRY },
          { name: 'Coconut Milk', category: IngredientCategory.PANTRY },
          { name: 'Parmesan Cheese', category: IngredientCategory.PANTRY },
          { name: 'Feta Cheese', category: IngredientCategory.PANTRY },
          { name: 'Mozzarella Cheese', category: IngredientCategory.PANTRY },
          { name: 'Cheddar Cheese', category: IngredientCategory.PANTRY },
          { name: 'Cream Cheese', category: IngredientCategory.PANTRY },
          { name: 'Sour Cream', category: IngredientCategory.PANTRY },
          { name: 'Butter', category: IngredientCategory.PANTRY },
          { name: 'Heavy Cream', category: IngredientCategory.PANTRY },
          { name: 'Half and Half', category: IngredientCategory.PANTRY },
          { name: 'Whipped Cream', category: IngredientCategory.PANTRY },
          { name: 'Ricotta Cheese', category: IngredientCategory.PANTRY },
          { name: 'Mascarpone', category: IngredientCategory.PANTRY },
          { name: 'Goat Cheese', category: IngredientCategory.PANTRY },
          { name: 'Blue Cheese', category: IngredientCategory.PANTRY },
          { name: 'Ghee', category: IngredientCategory.PANTRY },
          { name: 'Buttermilk', category: IngredientCategory.PANTRY },
        ],
      },
      // PANTRY ITEMS
      {
        category: IngredientCategory.PANTRY,
        title: 'Pantry Items',
        options: [
          // Grains & Starches
          { name: 'Brown Rice', category: IngredientCategory.PANTRY },
          { name: 'White Rice', category: IngredientCategory.PANTRY },
          { name: 'Jasmine Rice', category: IngredientCategory.PANTRY },
          { name: 'Quinoa', category: IngredientCategory.PANTRY },
          { name: 'Whole Wheat Pasta', category: IngredientCategory.PANTRY },
          { name: 'Regular Pasta', category: IngredientCategory.PANTRY },
          { name: 'Sweet Potato', category: IngredientCategory.PANTRY },
          { name: 'Regular Potato', category: IngredientCategory.PANTRY },
          { name: 'Oats', category: IngredientCategory.PANTRY },
          { name: 'Steel Cut Oats', category: IngredientCategory.PANTRY },
          { name: 'Whole Grain Bread', category: IngredientCategory.PANTRY },
          { name: 'Tortillas', category: IngredientCategory.PANTRY },
          { name: 'Corn Tortillas', category: IngredientCategory.PANTRY },
          { name: 'Barley', category: IngredientCategory.PANTRY },
          { name: 'Farro', category: IngredientCategory.PANTRY },
          { name: 'Bulgur', category: IngredientCategory.PANTRY },
          { name: 'Couscous', category: IngredientCategory.PANTRY },
          { name: 'Wild Rice', category: IngredientCategory.PANTRY },
          { name: 'Polenta', category: IngredientCategory.PANTRY },
          { name: 'Breadcrumbs', category: IngredientCategory.PANTRY },
          // Canned & Preserved
          { name: 'Canned Tomatoes', category: IngredientCategory.PANTRY },
          { name: 'Tomato Paste', category: IngredientCategory.PANTRY },
          { name: 'Black Beans (Canned)', category: IngredientCategory.PANTRY },
          { name: 'Kidney Beans (Canned)', category: IngredientCategory.PANTRY },
          { name: 'Chickpeas (Canned)', category: IngredientCategory.PANTRY },
          { name: 'Corn (Frozen)', category: IngredientCategory.PANTRY },
          { name: 'Peas (Frozen)', category: IngredientCategory.PANTRY },
          // Broths & Stocks
          { name: 'Chicken Broth', category: IngredientCategory.PANTRY },
          { name: 'Vegetable Broth', category: IngredientCategory.PANTRY },
          { name: 'Beef Broth', category: IngredientCategory.PANTRY },
          // Oils & Fats
          { name: 'Olive Oil', category: IngredientCategory.PANTRY },
          { name: 'Coconut Oil', category: IngredientCategory.PANTRY },
          { name: 'Avocado Oil', category: IngredientCategory.PANTRY },
          { name: 'Sesame Oil', category: IngredientCategory.PANTRY },
          { name: 'Peanut Butter', category: IngredientCategory.PANTRY },
          { name: 'Almond Butter', category: IngredientCategory.PANTRY },
          { name: 'Cashew Butter', category: IngredientCategory.PANTRY },
          { name: 'Tahini', category: IngredientCategory.PANTRY },
          { name: 'Olives', category: IngredientCategory.PANTRY },
          { name: 'Sun Dried Tomatoes', category: IngredientCategory.PANTRY },
          { name: 'Hearts of Palm', category: IngredientCategory.PANTRY },
          // Sauces & Condiments
          { name: 'Soy Sauce', category: IngredientCategory.PANTRY },
          { name: 'Tamari', category: IngredientCategory.PANTRY },
          { name: 'Worcestershire Sauce', category: IngredientCategory.PANTRY },
          { name: 'Salsa', category: IngredientCategory.PANTRY },
          { name: 'Hot Sauce', category: IngredientCategory.PANTRY },
          { name: 'Mayonnaise', category: IngredientCategory.PANTRY },
          { name: 'Mustard', category: IngredientCategory.PANTRY },
          { name: 'Ketchup', category: IngredientCategory.PANTRY },
          { name: 'BBQ Sauce', category: IngredientCategory.PANTRY },
          { name: 'Balsamic Vinegar', category: IngredientCategory.PANTRY },
          { name: 'Apple Cider Vinegar', category: IngredientCategory.PANTRY },
          { name: 'Rice Vinegar', category: IngredientCategory.PANTRY },
          // Sweeteners
          { name: 'Honey', category: IngredientCategory.PANTRY },
          { name: 'Maple Syrup', category: IngredientCategory.PANTRY },
          // Spices & Herbs
          { name: 'Garlic', category: IngredientCategory.PANTRY },
          { name: 'Ginger', category: IngredientCategory.PANTRY },
          { name: 'Cinnamon', category: IngredientCategory.PANTRY },
          { name: 'Paprika', category: IngredientCategory.PANTRY },
          { name: 'Cumin', category: IngredientCategory.PANTRY },
          { name: 'Curry Powder', category: IngredientCategory.PANTRY },
          { name: 'Chili Powder', category: IngredientCategory.PANTRY },
          { name: 'Oregano', category: IngredientCategory.PANTRY },
          { name: 'Basil', category: IngredientCategory.PANTRY },
          { name: 'Thyme', category: IngredientCategory.PANTRY },
          { name: 'Rosemary', category: IngredientCategory.PANTRY },
          { name: 'Turmeric', category: IngredientCategory.PANTRY },
          { name: 'Garlic Powder', category: IngredientCategory.PANTRY },
          { name: 'Onion Powder', category: IngredientCategory.PANTRY },
          { name: 'Coriander', category: IngredientCategory.PANTRY },
          { name: 'Cardamom', category: IngredientCategory.PANTRY },
          { name: 'Nutmeg', category: IngredientCategory.PANTRY },
          { name: 'Allspice', category: IngredientCategory.PANTRY },
          { name: 'Cloves', category: IngredientCategory.PANTRY },
          { name: 'Cilantro', category: IngredientCategory.PANTRY },
          { name: 'Crushed Red Pepper', category: IngredientCategory.PANTRY },
          { name: 'Old Bay', category: IngredientCategory.PANTRY },
          { name: 'Jalapeño', category: IngredientCategory.PANTRY },
          { name: 'Serrano Pepper', category: IngredientCategory.PANTRY },
          { name: 'Habanero', category: IngredientCategory.PANTRY },
          { name: 'Cayenne Pepper', category: IngredientCategory.PANTRY },
          { name: 'Thai Chili', category: IngredientCategory.PANTRY },
          { name: 'Chipotle', category: IngredientCategory.PANTRY },
          { name: 'Poblano', category: IngredientCategory.PANTRY },
          { name: 'Anaheim Pepper', category: IngredientCategory.PANTRY },
          // Baking
          { name: 'Flour', category: IngredientCategory.PANTRY },
          { name: 'Baking Powder', category: IngredientCategory.PANTRY },
          { name: 'Baking Soda', category: IngredientCategory.PANTRY },
          { name: 'Vanilla Extract', category: IngredientCategory.PANTRY },
        ],
      },
    ]
  }

  /**
   * Get only protein options
   */
  getProteinOptions(): IngredientOption[] {
    const groups = this.getIngredientOptions()
    const proteinGroup = groups.find(g => g.category === IngredientCategory.PROTEIN)
    return proteinGroup?.options || []
  }
}

export default MealInspirationService
