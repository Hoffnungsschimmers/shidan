package com.fanji.mealnote.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fanji.mealnote.ui.add.AddRestaurantScreen
import com.fanji.mealnote.ui.detail.RestaurantDetailScreen
import com.fanji.mealnote.ui.edit.EditRestaurantScreen
import com.fanji.mealnote.ui.edit.EditVisitScreen
import com.fanji.mealnote.ui.home.MainScaffold
import com.fanji.mealnote.ui.visit.AddVisitScreen
import com.fanji.mealnote.ui.visit.PickRestaurantScreen

/**
 * 全部路由。
 *
 * 结构上分为两层：
 * - [MAIN] 是唯一的「壳」路由，内部用底部导航切换三个标签页。**不把标签页拆成独立路由**，
 *   否则每个标签都会在返回栈里留下一条记录，用户按返回键会在标签间来回跳而不是退出应用。
 * - 其余都是压栈的二级页面（详情、表单、选店）。
 */
object Routes {
    const val MAIN = "main"

    /** 新建餐厅。[nextVisit] 为 true 时保存后直接进入用餐表单。 */
    const val ADD_RESTAURANT = "add_restaurant?nextVisit={nextVisit}"
    const val RESTAURANT_DETAIL = "restaurant/{restaurantId}"

    /**
     * 新建用餐记录。
     *
     * [copyFrom] 为「照上次再来一份」的来源记录 id，`-1` 表示不带入任何内容。
     * 用哨兵值而不是可空参数：Navigation 的可空 Long 实参在缺省值处理上很容易出错，
     * 而这里「无来源」与「来源是某条记录」的区分只需要一个不会冲突的负数。
     */
    const val ADD_VISIT = "restaurant/{restaurantId}/visit?copyFrom={copyFrom}"
    const val EDIT_RESTAURANT = "restaurant/{restaurantId}/edit"
    const val EDIT_VISIT = "visit/{recordId}/edit"
    const val PICK_RESTAURANT = "pick_restaurant"

    /** 「无来源记录」哨兵。记录 id 由数据库自增，恒为正数。 */
    const val NO_COPY_SOURCE = -1L

    fun addRestaurant(nextVisit: Boolean = false): String = "add_restaurant?nextVisit=$nextVisit"

    fun restaurantDetail(restaurantId: Long): String = "restaurant/$restaurantId"

    fun addVisit(restaurantId: Long, copyFromRecordId: Long = NO_COPY_SOURCE): String =
        "restaurant/$restaurantId/visit?copyFrom=$copyFromRecordId"

    fun editRestaurant(restaurantId: Long): String = "restaurant/$restaurantId/edit"

    fun editVisit(recordId: Long): String = "visit/$recordId/edit"
}

/**
 * 应用导航图。
 *
 * 转场动画统一为「新页面从右侧推入 + 淡入，旧页面向左退场」：
 * 横向位移量刻意小于屏幕宽度（`it / 6`），形成轻微视差而不是整屏平移 ——
 * 整屏平移在快速连续跳转时会让人失去方向感，小位移能保留「谁盖在谁上面」的直觉。
 */
@Composable
fun MealNoteApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { width -> width / 6 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(animationSpec = tween(200))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { width -> -width / 12 },
                animationSpec = tween(220),
            ) + fadeOut(animationSpec = tween(160))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { width -> -width / 12 },
                animationSpec = tween(220),
            ) + fadeIn(animationSpec = tween(200))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { width -> width / 6 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeOut(animationSpec = tween(160))
        },
    ) {
        composable(Routes.MAIN) {
            MainScaffold(
                onOpenRestaurant = { id -> navController.navigate(Routes.restaurantDetail(id)) },
                onAddRestaurant = { navController.navigate(Routes.addRestaurant()) },
                onStartVisit = { navController.navigate(Routes.PICK_RESTAURANT) },
            )
        }

        composable(
            route = Routes.ADD_RESTAURANT,
            arguments = listOf(
                navArgument("nextVisit") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val nextVisit = backStackEntry.arguments?.getBoolean("nextVisit") ?: false
            AddRestaurantScreen(
                continueToVisit = nextVisit,
                onBack = { navController.popBackStack() },
                onSaved = { newId ->
                    if (nextVisit) {
                        navController.openVisitForm(newId)
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(Routes.PICK_RESTAURANT) {
            PickRestaurantScreen(
                onBack = { navController.popBackStack() },
                onPick = { id -> navController.openVisitForm(id) },
                onCreateNew = { navController.navigate(Routes.addRestaurant(nextVisit = true)) },
            )
        }

        composable(
            route = Routes.RESTAURANT_DETAIL,
            arguments = listOf(navArgument("restaurantId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getLong("restaurantId") ?: return@composable
            RestaurantDetailScreen(
                restaurantId = restaurantId,
                onBack = { navController.popBackStack() },
                onAddVisit = { navController.openVisitForm(restaurantId) },
                onCopyLastVisit = { recordId ->
                    navController.openVisitForm(restaurantId, copyFromRecordId = recordId)
                },
                onEditRestaurant = { navController.navigate(Routes.editRestaurant(restaurantId)) },
                onEditVisit = { recordId -> navController.navigate(Routes.editVisit(recordId)) },
            )
        }

        composable(
            route = Routes.ADD_VISIT,
            arguments = listOf(
                navArgument("restaurantId") { type = NavType.LongType },
                navArgument("copyFrom") {
                    type = NavType.LongType
                    defaultValue = Routes.NO_COPY_SOURCE
                },
            ),
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getLong("restaurantId") ?: return@composable
            val copyFrom = backStackEntry.arguments?.getLong("copyFrom") ?: Routes.NO_COPY_SOURCE
            AddVisitScreen(
                restaurantId = restaurantId,
                copyFromRecordId = copyFrom.takeIf { it != Routes.NO_COPY_SOURCE },
                onBack = { navController.popBackStack() },
                onSaved = { navController.backToDetail(restaurantId) },
            )
        }

        composable(
            route = Routes.EDIT_RESTAURANT,
            arguments = listOf(navArgument("restaurantId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getLong("restaurantId") ?: return@composable
            EditRestaurantScreen(
                restaurantId = restaurantId,
                onBack = { navController.popBackStack() },
                // 详情页数据来自 Room 订阅，返回后会自动刷新为最新内容。
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EDIT_VISIT,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: return@composable
            EditVisitScreen(
                recordId = recordId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}

/**
 * 打开用餐表单，并把返回栈压平到只剩「壳 + 表单」。
 *
 * 进入用餐表单的路径有三条（从清单选店、从详情页、新建店铺后接着填），
 * 如果各自保留中间的选店页/新建页，用户按返回键会退回到一个已经完成使命的中间页面。
 * 统一清到 [Routes.MAIN] 之后，返回键的行为在任何入口下都一致：退回主界面。
 */
private fun NavHostController.openVisitForm(
    restaurantId: Long,
    copyFromRecordId: Long = Routes.NO_COPY_SOURCE,
) {
    navigate(Routes.addVisit(restaurantId, copyFromRecordId)) {
        popUpTo(Routes.MAIN) { inclusive = false }
    }
}

/**
 * 保存用餐记录后跳转到餐厅详情。
 *
 * 同样清到 [Routes.MAIN]，使详情页成为返回栈中唯一的上层页面 ——
 * 用户看完刚写的记录，再按返回就是清单/足迹，而不是回到已经提交过的表单。
 */
private fun NavHostController.backToDetail(restaurantId: Long) {
    navigate(Routes.restaurantDetail(restaurantId)) {
        popUpTo(Routes.MAIN) { inclusive = false }
    }
}
