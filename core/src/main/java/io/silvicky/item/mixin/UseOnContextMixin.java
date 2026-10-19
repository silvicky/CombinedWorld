package io.silvicky.item.mixin;

/**
 * Retired. This used to force the band's level into {@code UseOnContext}'s constructor.
 *
 * <p>It crashed every client right-click with
 * {@code VerifyError: Bad type on operand stack ... uninitializedThis is not assignable to UseOnContext}.
 * The cause is structural: a constructor's {@code this} is {@code uninitializedThis} until the delegated
 * constructor has run, so MixinExtras cannot pass the mixin instance as the handler's receiver. Making the
 * handler {@code static} would have fixed the verifier error but left a common-class mixin running on the
 * client for every placement, for no reason.
 *
 * <p>The logic now lives in {@code ServerPlayerGameModeMixin#abyss$placeContext}, which wraps the
 * {@code new UseOnContext(...)} inside {@code useItemOn} - a server-only method, so the client is untouched and
 * the constructor is never rewritten.
 *
 * <p>This file is kept only because the session could not delete it. Remove it with
 * {@code git rm core/src/main/java/io/silvicky/item/mixin/UseOnContextMixin.java}; it is no longer registered in
 * {@code item-storage.mixins.json} and contains no mixin.
 */
final class UseOnContextMixin
{
    private UseOnContextMixin() {}
}
